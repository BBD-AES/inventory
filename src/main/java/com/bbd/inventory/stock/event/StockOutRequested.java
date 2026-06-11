package com.bbd.inventory.stock.event;


import java.util.List;

public record StockOutRequested(
        String eventId,
        String source,
        String eventType,
        String occurredAt,
        String soNumber,
        List<Line> lines) {

    public record Line(
            String sku,
            int quantity,
            String warehouseCode,
            int unitPrice) {}
}

