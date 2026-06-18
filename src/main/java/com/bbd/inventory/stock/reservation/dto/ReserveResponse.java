package com.bbd.inventory.stock.reservation.dto;

public record ReserveResponse(
        Long reservationId,
        String soNumber,
        String sku,
        String warehouseCode,
        int requested,
        int reserved,
        int remainingRequested,
        int availableAfter
) {}
