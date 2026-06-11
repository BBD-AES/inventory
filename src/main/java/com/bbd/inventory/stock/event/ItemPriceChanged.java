package com.bbd.inventory.stock.event;

public record ItemPriceChanged(
        String eventId,
        String source,
        String eventType,
        String occurredAt,
        String sku,
        int unitPrice) {}

