package com.bbd.inventory.stock.consumer.event;

import java.time.Instant;

/**
 * Fact emitted by the STOCKTAKE / OPS service after a physical count correction.
 * The COUNT is born in that service; inventory CONSUMES the result and records the
 * signed difference as a stock_movement (counted - current).
 *
 * Topic: stocktake.count-adjusted  ->  binding countAdjusted-in-0
 *
 * event_id + tenancy_id travel as message HEADERS (CONVENTION §8.2).
 */
public record CountAdjusted(
        String sku,
        String warehouseCode,
        int countedQuantity,
        String reason,
        Instant occurredAt
) {}
