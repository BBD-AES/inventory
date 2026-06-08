package com.bbd.inventory.stock.command.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateStockCommand(
        @NotBlank String warehouseCode,
        @NotBlank String sku,
        @PositiveOrZero int quantity,
        String location
) {}
