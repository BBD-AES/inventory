package com.bbd.inventory.stock.query.dto;

import com.bbd.inventory.warehouse.WarehouseType;

import java.util.List;

public record StockSummaryResponse(
        long totalWarehouses,
        long activeWarehouses,
        long totalSkus,
        long totalCurrentStock,
        long totalAvailableStock,
        long belowSafetyCount,
        List<WarehouseStock> warehouses             // active only
) {
    public record WarehouseStock(
            String code,
            String name,
            WarehouseType type,
            boolean active,
            long skuCount,
            long currentStock) {}
}
