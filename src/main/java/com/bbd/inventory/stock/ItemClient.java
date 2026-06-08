package com.bbd.inventory.stock;

import java.util.Optional;

/*
    the interface the command service depends on
 */
public interface ItemClient {

    // GET /api/v1/items/{sku}
    Optional<ItemInfo> getBySku(String sku);            // sku validation

}
