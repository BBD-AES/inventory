package com.bbd.inventory.stock.command;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.*;
import com.bbd.inventory.stock.command.dto.CreateStockCommand;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import com.bbd.inventory.stock.event.ItemPriceChanged;
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
    public Long createStock(CreateStockCommand cmd) {
        // resolve the warehouse (local, authoritative to inventory)
        Warehouse warehouse = warehouseRepository.findByCode(cmd.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        // one stock row per (warehouse, sku) -- guard duplicates
        if (stockRepository.existsByWarehouseAndSku(warehouse, cmd.sku())) {
            throw new ApiException(ErrorCode.STOCK_ALREADY_EXISTS);
        }

        // SYNC cross-service validation + fetch the catalog facts to denormalize
        ItemInfo item = itemClient.getBySku(cmd.sku())
                .orElseThrow(() -> new ApiException(ErrorCode.SKU_NOT_FOUND));
        return 0L;
    }

    @Transactional
    public StockLocationResponse changeLocation(
            String sku, String warehouseCode, UpdateLocationCommand cmd) {

        Stock stock = stockRepository.findBySkuAndWarehouseCode(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        // entity method; dirty-checked, no movement
        stock.changeLocation(cmd.location());

        stockRepository.flush();

        return new StockLocationResponse(
                sku, warehouseCode, stock.getLocation(), stock.getUpdatedAt()
        );
    }

    @Transactional
    public void outbound(StockOutRequested e) {
        // for each outbound lines
        for (StockOutRequested.Line line : e.lines()) {
            Stock stock = stockRepository.findBySkuAndWarehouseCode(line.sku(), line.warehouseCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

            stock.applyOut(line.quantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(line.sku())
                    .warehouseCode(line.warehouseCode())
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
            Stock stock = stockRepository.findBySkuAndWarehouseCode(line.sku(), line.warehouseCode())
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
    public void updateUnitPrice(ItemPriceChanged e) {
        stockRepository.updateUnitPriceBySku(e.sku(), e.unitPrice());
    }
}
