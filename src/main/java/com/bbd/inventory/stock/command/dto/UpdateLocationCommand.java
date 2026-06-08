package com.bbd.inventory.stock.command.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLocationCommand(
        @NotBlank String location
) {}
