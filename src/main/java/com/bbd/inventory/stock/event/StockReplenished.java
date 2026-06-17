package com.bbd.inventory.stock.event;

import java.util.List;

public record StockReplenished(
        // UUID - consumer (sales) idempotency key
        String eventId,
        String source,
        String eventType,
        String occurredAt,
        String soNumber,
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity,
            String warehouseCode
    ) {}
}
