package com.bbd.inventory.stock.consumer.event;

import java.time.Instant;

/**
 * Fact emitted by the PROCUREMENT service when a vendor's goods physically arrive
 * against a Purchase Order. Inventory CONSUMES this (it does not own the fact) and
 * turns it into a +quantity stock_movement. See PROCUREMENT_INBOUND.md.
 *
 * Topic: procurement.goods-inbound  ->  binding goodsInbound-in-0
 *
 * event_id + tenancy_id travel as message HEADERS (CONVENTION §8.2), not in this body.
 * This record is only the immutable business fact (CONVENTION §9).
 */
public record GoodsInbound(
        String poNumber,
        String sku,
        String warehouseCode,
        int quantity,
        Instant occurredAt
) {}
