package com.bbd.inventory.stock.query.dto;

import java.util.List;

/**
 * Dashboard aggregation for stock health (#41).
 *  - safetyShortage: rows where availableStock < safetyStock, plus the total count.
 *  - byWarehouse: per-warehouse available-stock sum and below-safety SKU count.
 */
public record StockStatsResponse(
        SafetyShortage safetyShortage,
        List<ByWarehouse> byWarehouse
) {
    public record SafetyShortage(
            long count,
            List<Item> items
    ) {
        public record Item(
                String sku,
                String warehouseCode,
                int currentStock,
                int availableStock,
                int safetyStock,
                int shortage
        ) {}
    }

    public record ByWarehouse(
            String warehouseCode,
            long availableSum,
            long shortageCount
    ) {}
}
