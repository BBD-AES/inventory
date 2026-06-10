package com.bbd.inventory.stock.command.dto;

/**
 * Internal command built from a consumed StockTransferRequested event (NOT an HTTP body).
 * eventId is the de-dupe key written to processed_events in the same tx (CONVENTION §12).
 */
public record TransferStockCommand(
        String eventId,
        String tenancyId,
        String sku,
        String fromWarehouseCode,
        String toWarehouseCode,
        int quantity
) {}
