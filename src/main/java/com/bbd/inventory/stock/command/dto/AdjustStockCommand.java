package com.bbd.inventory.stock.command.dto;

/**
 * Internal command built from a consumed CountAdjusted event (NOT an HTTP body).
 * countedQuantity is the physical count; the service computes amountDiff = counted - current.
 * eventId is the de-dupe key written to processed_events in the same tx (CONVENTION §12).
 */
public record AdjustStockCommand(
        String eventId,
        String tenancyId,
        String sku,
        String warehouseCode,
        int countedQuantity,
        String reason
) {}
