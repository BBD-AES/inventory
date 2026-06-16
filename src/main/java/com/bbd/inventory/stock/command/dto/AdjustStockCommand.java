package com.bbd.inventory.stock.command.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Internal stock correction (재고 실사 보정). currentStock is the absolute target value;
 * referenceNumber carries the reason / 실사 문서번호 for the ADJUST movement audit row.
 */
public record AdjustStockCommand(
        @NotNull @PositiveOrZero Integer currentStock,
        @NotBlank String referenceNumber
) {}
