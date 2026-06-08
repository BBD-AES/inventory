package com.bbd.inventory.stock;

/*
    domain-facing shape rest of inventory uses.
    ItemResponse -> values from the wire
    ItemInfo -> what inventory agreed to depend on
 */
public record ItemInfo(
        String sku,
        String name,
        String category,
        int safetyStock
) {}
