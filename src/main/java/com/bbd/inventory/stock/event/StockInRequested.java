package com.bbd.inventory.stock.event;

import java.util.List;

public record StockInRequested(
        String eventId,
        String source,
        String eventType,
        String occurredAt,
        String poNumber,
        String soId,
        List<Line> lines) {

    public record Line(
            String sku,
            int quantity,
            String warehouseCode,
            int unitPrice,
            String name,
            String unit,
            String category,
            int safetyStock) {}
}
