package com.bbd.inventory.stock.event;

public record ItemChanged(
        String eventId,
        String source,
        String eventType,
        String occurredAt,
        String sku,
        Integer unitPrice,              // boxed - null on ACTIVE events
        Boolean active
) {}

