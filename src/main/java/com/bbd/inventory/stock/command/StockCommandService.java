package com.bbd.inventory.stock.command;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.*;
import com.bbd.inventory.stock.command.dto.AdjustStockCommand;
import com.bbd.inventory.stock.command.dto.StockAdjustResponse;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import com.bbd.inventory.stock.event.StockInRequested;
import com.bbd.inventory.stock.event.StockOutRequested;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;      // cross-context read
    private final ItemClient itemClient;                        // cross-service read
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public StockLocationResponse changeLocation(
            String sku, String warehouseCode, UpdateLocationCommand cmd) {

        Stock stock = stockRepository.findForUpdate(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        // entity method; dirty-checked, no movement
        stock.changeLocation(cmd.location());

        stockRepository.flush();

        return new StockLocationResponse(
                sku, warehouseCode, stock.getLocation(), stock.getUpdatedAt()
        );
    }

    /**
     * Internal stock correction: set currentStock to the target and record an ADJUST movement
     * (signed delta) so the ledger stays consistent (Σ IN − Σ OUT + Σ ADJUST = currentStock).
     */
    @Transactional
    public StockAdjustResponse adjust(
            String sku, String warehouseCode, AdjustStockCommand cmd) {

        Stock stock = stockRepository.findForUpdate(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        int delta = stock.correctTo(cmd.currentStock());

        stockMovementRepository.save(StockMovement.builder()
                .sku(sku)
                .warehouseCode(warehouseCode)
                .type(MovementType.ADJUST)
                .quantity(delta)                       // signed: +/- correction
                .unitPrice(stock.getUnitPrice())       // current replica price as snapshot
                .referenceNumber(cmd.referenceNumber())
                .build());

        stockRepository.flush();

        return new StockAdjustResponse(
                sku, warehouseCode, stock.getCurrentStock(),
                stock.getAvailableStock(), delta, stock.getUpdatedAt());
    }

    @Transactional
    public void outbound(StockOutRequested e) {
        // for each outbound lines
        for (StockOutRequested.Line line : e.lines()) {
            Stock stock = stockRepository.findForUpdate(line.sku(), line.warehouseCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

            stock.applyOut(line.quantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(line.sku())
                    .warehouseCode(line.warehouseCode())
                    .type(MovementType.OUT)
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .referenceNumber(e.soNumber())
                    .build());
        }
    }

    /*
        procurement.stock-in-requested -> +qty (create if missing) +
        movement(IN).
     */
    @Transactional
    public void inbound(StockInRequested e) {

        for (StockInRequested.Line line : e.lines()) {
            Stock stock = stockRepository.findForUpdate(line.sku(), line.warehouseCode())
                    .orElseGet(() -> createForInbound(line));

            stock.applyIn(line.quantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(line.sku())
                    .warehouseCode(line.warehouseCode())
                    .type(MovementType.IN)
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .referenceNumber(e.poNumber())
                    .build());
        }

    }

    private Stock createForInbound(StockInRequested.Line line) {

        Warehouse warehouse = warehouseRepository.findByCode(line.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        ItemInfo item = itemClient.getBySku(line.sku())
                .orElseThrow(() -> new ApiException(ErrorCode.SKU_NOT_FOUND));

        return stockRepository.save(Stock.builder()
                .warehouse(warehouse)
                .sku(item.sku())
                .name(item.name())
                .unit(item.unit())
                .currentStock(0)
                .availableStock(0)
                .safetyStock(item.safetyStock())
                .category(item.category())
                .location(null)
                .unitPrice(item.unitPrice())
                .build());
    }

    @Transactional
    public void updateUnitPrice(String sku, int unitPrice) {
        stockRepository.updateUnitPriceBySku(sku, unitPrice);
    }

    @Transactional
    public void updateActive(String sku, boolean active) {
        stockRepository.updateActiveBySku(sku, active);
    }
}
