package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ItemRequest(
        @NotNull(message = "Item category is required")
        UUID itemCategoryId,

        @NotBlank(message = "Item name is required")
        String name,

        @NotNull(message = "Credit price is required")
        @Positive(message = "Credit price must be greater than 0")
        BigDecimal creditPrice,

        @NotNull(message = "Cash price is required")
        @Positive(message = "Cash price must be greater than 0")
        BigDecimal cashPrice,

        @NotBlank(message = "Unit is required")
        @Pattern(regexp = "Bag|Bottle|Pkt", message = "Unit must be one of Bag, Bottle, Pkt")
        String unit
) {
}
