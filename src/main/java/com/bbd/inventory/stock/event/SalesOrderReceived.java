package com.bbd.inventory.stock.event;

/**
 * sales.order.received envelope. Key = soNumber, no lines:
 * inventory resolves what/where/how-much from its own RESERVED rows.
 */
public record SalesOrderReceived(
        String eventId,
        String eventType,
        String soNumber,
        String occurredAt) {}
