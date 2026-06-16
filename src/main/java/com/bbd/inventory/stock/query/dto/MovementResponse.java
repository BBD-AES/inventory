package com.bbd.inventory.stock.query.dto;

import com.bbd.inventory.stock.MovementType;
import com.bbd.inventory.stock.StockMovement;

import java.time.Instant;

public record MovementResponse(
        Long id,
        MovementType type,
        String sku,
        String warehouseCode,
        int quantity,
        int unitPrice,
        String referenceNumber,
        Instant occurredAt
) {
    public static MovementResponse from(StockMovement m) {
        return new MovementResponse(
                m.getId(), m.getType(), m.getSku(), m.getWarehouseCode(),
                m.getQuantity(), m.getUnitPrice(), m.getReferenceNumber(), m.getOccurredAt());
    }
}
