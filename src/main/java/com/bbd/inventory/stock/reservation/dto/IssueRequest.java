package com.bbd.inventory.stock.reservation.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueRequest(
        @NotBlank String soNumber
) {}
