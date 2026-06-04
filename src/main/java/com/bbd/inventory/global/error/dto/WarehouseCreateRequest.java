package com.bbd.inventory.global.error.dto;


import com.bbd.inventory.warehouse.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WarehouseCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull WarehouseType type,
        String address
) {}
