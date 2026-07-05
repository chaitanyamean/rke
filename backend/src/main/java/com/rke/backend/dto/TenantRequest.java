package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$",
                 message = "Slug may only contain lowercase letters, digits and hyphens")
        String slug,

        /** CSS hex color (e.g. {@code #1e40af}). Optional. */
        @Pattern(regexp = "^(#[0-9a-fA-F]{3,6})?$",
                 message = "primaryColor must be a valid hex color (e.g. #1e40af) or empty")
        String primaryColor,

        boolean active
) {
}
