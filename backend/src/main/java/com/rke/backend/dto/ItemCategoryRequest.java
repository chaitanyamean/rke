package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ItemCategoryRequest(
        @NotBlank(message = "Category name is required")
        String name,

        String description
) {
}
