package com.bbd.inventory.stock.command.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 수주 종료 출고(#69) REST 요청. sales 가 CO 종료 시 지정 창고 재고를 차감하려고 호출.
 * referenceNumber = coNumber(원장 참조 + 멱등 키). 라인 구조는 sales 의 StockOutboundRequest 와 정합.
 * 부족 시 inventory 가 409(INSUFFICIENT_STOCK) → sales 가 종료 차단(CO007).
 */
public record StockOutboundRequest(
        @NotBlank String referenceNumber,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotBlank String sku,
            @Positive int quantity,
            @NotBlank String warehouseCode,
            int unitPrice
    ) {}
}
