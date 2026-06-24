package com.bbd.inventory.stock.command;

import com.bbd.inventory.stock.MovementType;
import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.StockMovement;
import com.bbd.inventory.stock.StockMovementRepository;
import com.bbd.inventory.stock.StockRepository;
import com.bbd.inventory.stock.event.StockInRequested;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import com.bbd.inventory.warehouse.WarehouseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA #5/#7 (창고 receive 했는데 재고 안 올라감): when no stock row exists for the
 * warehouse+sku, inbound auto-creates it, increments quantity, and records an IN
 * movement. Proves the inventory-side logic is correct (the QA's "no row" symptom
 * is already handled). Pure Mockito unit test — no Kafka/DB.
 */
@ExtendWith(MockitoExtension.class)
class StockCommandServiceInboundTest {

    @Mock StockRepository stockRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @InjectMocks StockCommandService service;

    @Test
    void inbound_missingRow_createsStockIncrementsAndRecordsMovement() {
        Warehouse wh = Warehouse.create("WH001", "Gangnam", WarehouseType.DEALER, "Seoul");
        when(stockRepository.findForUpdate("SKU-1", "WH001")).thenReturn(Optional.empty());
        when(warehouseRepository.findByCode("WH001")).thenReturn(Optional.of(wh));
        when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArgument(0));

        service.inbound(event("WH001", "SKU-1", 10));

        ArgumentCaptor<Stock> stock = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stock.capture());                 // row auto-created
        assertThat(stock.getValue().getCurrentStock()).isEqualTo(10);  // applyIn ran
        assertThat(stock.getValue().getAvailableStock()).isEqualTo(10);

        ArgumentCaptor<StockMovement> mv = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(mv.capture());            // IN ledger row recorded
        assertThat(mv.getValue().getType()).isEqualTo(MovementType.IN);
        assertThat(mv.getValue().getQuantity()).isEqualTo(10);
        assertThat(mv.getValue().getWarehouseCode()).isEqualTo("WH001");
    }

    private static StockInRequested event(String wh, String sku, int qty) {
        return new StockInRequested(
                "evt-1", "procurement", "STOCK_IN_REQUESTED", "2026-06-24T00:00:00Z",
                "PO-1", null,
                List.of(new StockInRequested.Line(
                        sku, qty, wh, 1000, "Item", "EA", "CAT", true, "BUY", 5)));
    }
}
