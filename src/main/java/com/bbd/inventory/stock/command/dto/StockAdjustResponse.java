package com.bbd.inventory.stock.command.dto;

import java.time.Instant;

public record StockAdjustResponse(
        String sku,
        String warehouseCode,
        int currentStock,
        int availableStock,
        int delta,
        Instant updatedAt
) {}
