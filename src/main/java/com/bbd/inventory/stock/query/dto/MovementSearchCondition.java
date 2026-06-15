package com.bbd.inventory.stock.query.dto;

import com.bbd.inventory.stock.MovementType;

import java.time.Instant;

/**
 * Filter inputs for GET /api/v1/stock-movements. All fields optional;
 * null means "no filter on this dimension". from/to bound the occurredAt range (inclusive).
 */
public record MovementSearchCondition(
        MovementType movementType,
        String sku,
        String warehouseCode,
        Instant from,
        Instant to
) {}
