package com.bbd.inventory.stock.reservation.dto;

import java.util.List;

public record ReleaseResponse(
        String soNumber,
        List<Line> lines
) {
    public record Line(String warehouseCode,
                       String sku,
                       int quantity,
                       String status) {}
}

