package com.bbd.inventory.stock.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(
        @NotBlank String requestId,
        @NotBlank String soNumber,
        @NotBlank String sku,
        @NotBlank String warehouseCode,
        @NotNull @Positive Integer quantity
) {}
