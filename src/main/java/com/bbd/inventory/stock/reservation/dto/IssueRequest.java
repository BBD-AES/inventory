package com.bbd.inventory.stock.reservation.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueRequest(
        @NotBlank String soNumber,
        // 동기 issue 경로의 도착 지점(선택). 미지정(null)이면 출처 OUT만 수행 — 기존 호출 backward-compat.
        String toWarehouseCode
) {}
