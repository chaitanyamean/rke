package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record VillageRequest(
        @NotBlank(message = "Village name is required")
        String name,

        String landmark
) {
}
