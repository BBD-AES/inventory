package com.bbd.inventory.stock.query.dto;

import com.bbd.inventory.stock.Stock;

import java.util.List;

public record AvailabilityResponse(
        String sku,
        List<WarehouseAvailability> warehouses) {

    public record WarehouseAvailability(
            String warehouseCode,
            String warehouseName,
            int onHand,
            int reserved,
            int available
    ) {}
    public static AvailabilityResponse from(String sku, List<Stock> rows) {
        List<WarehouseAvailability> warehouses = rows.stream()
                .map(s -> new WarehouseAvailability(
                        s.getWarehouse().getCode(),
                        s.getWarehouse().getName(),
                        s.getCurrentStock(),
                        s.getCurrentStock() - s.getAvailableStock(),    // reserved logic
                        s.getAvailableStock()
                )).toList();

        return new AvailabilityResponse(sku, warehouses);
    }
}
