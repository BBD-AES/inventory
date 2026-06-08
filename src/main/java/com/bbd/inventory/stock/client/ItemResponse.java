package com.bbd.inventory.stock.client;

/*
    this record is a defensive copy of the boundary.
 */
public record ItemResponse(
        String sku,
        String name,
        String category,            // labeled in KR
        int safetyStock
) {}
