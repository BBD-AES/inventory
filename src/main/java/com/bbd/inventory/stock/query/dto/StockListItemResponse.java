package com.bbd.inventory.stock.query.dto;


import com.bbd.inventory.stock.Stock;

public record StockListItemResponse(
        String sku,
        String name,
        int currentStock,
        String warehouseCode
) {
    public static StockListItemResponse from(Stock s) {
        return new StockListItemResponse(s.getSku(), s.getName(),
                s.getCurrentStock(), s.getWarehouse().getCode());
    }
}
