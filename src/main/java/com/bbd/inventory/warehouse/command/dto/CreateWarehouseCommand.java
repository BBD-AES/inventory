package com.bbd.inventory.warehouse.command.dto;

import com.bbd.inventory.warehouse.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWarehouseCommand(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull WarehouseType type,
        @NotBlank String address
) {}
