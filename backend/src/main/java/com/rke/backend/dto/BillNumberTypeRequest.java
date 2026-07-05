package com.rke.backend.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BillNumberTypeRequest(
        @NotBlank(message = "Bill number type name is required")
        String name,

        @NotNull(message = "Item category is required")
        UUID itemCategoryId,

        String description
) {
}
