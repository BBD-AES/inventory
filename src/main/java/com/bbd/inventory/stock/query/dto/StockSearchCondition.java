package com.bbd.inventory.stock.query.dto;

/**
 * Filter inputs for GET /api/v1/stocks. All fields optional;
 * null / false means "no filter on this dimension".
 */
public record StockSearchCondition(
        String warehouseCode,
        String sku,
        String category,
        boolean belowSafety
) {}
