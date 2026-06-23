package com.bbd.inventory.stock.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(
        String requestId,  // 레거시 선택 폴백 — 멱등 키는 Idempotency-Key 헤더가 표준
        @NotBlank String soNumber,
        @NotBlank String sku,
        @NotBlank String warehouseCode,
        @NotNull @Positive Integer quantity
) {}
