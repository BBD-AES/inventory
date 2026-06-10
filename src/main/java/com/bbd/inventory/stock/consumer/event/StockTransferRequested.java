package com.bbd.inventory.stock.consumer.event;

import java.time.Instant;

/**
 * Fact emitted by the SALES service (HQ_MANAGER approved a transfer) telling inventory
 * to move stock between two warehouses it owns. Inventory CONSUMES this and records the
 * paired out/in stock_movements. EDA.md STEP 5.
 *
 * Topic: inventory.stock-transfer-requested  ->  binding stockTransferRequested-in-0
 *
 * event_id + tenancy_id travel as message HEADERS (CONVENTION §8.2).
 */
public record StockTransferRequested(
        String sku,
        String fromWarehouseCode,
        String toWarehouseCode,
        int quantity,
        Instant occurredAt
) {}
