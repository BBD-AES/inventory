package com.bbd.inventory.stock.command.dto;

/**
 * Internal command built from a consumed GoodsInbound event (NOT an HTTP body).
 * eventId is the de-dupe key written to processed_events in the same tx (CONVENTION §12).
 */
public record ReceiveStockCommand(
        String eventId,
        String tenancyId,
        String poNumber,
        String sku,
        String warehouseCode,
        int quantity
) {}
