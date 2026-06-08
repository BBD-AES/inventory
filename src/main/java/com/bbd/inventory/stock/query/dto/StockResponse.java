package com.bbd.inventory.stock.query.dto;

import com.bbd.inventory.stock.Stock;

import java.time.Instant;

public record StockResponse(
        String sku,
        String name,
        String category,
        String warehouseCode,
        int currentStock,
        int availableStock,
        int safetyStock,
        String location,
        Instant updatedAt
) {
    public static StockResponse from(Stock s) {
        return new StockResponse(
                s.getSku(),
                s.getName(),
                s.getCategory(),
                s.getWarehouse().getCode(),
                s.getCurrentStock(),
                s.getAvailableStock(),
                s.getSafetyStock(),
                s.getLocation(),
                s.getUpdatedAt()
        );
    }
}
