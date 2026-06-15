package com.bbd.inventory.stock.command.dto;

import java.time.Instant;

public record StockLocationResponse(
        String sku,
        String warehouseCode,
        String location,
        Instant updatedAt
) {}
