package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a brand-new tenant together with its first admin login,
 * in a single atomic step. Unlike {@link TenantRequest} (used for updates), this
 * also carries the credentials for the tenant's initial {@code staff_users} row
 * (role {@code admin}) so a newly created tenant is immediately usable.
 */
public record TenantCreateRequest(

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

        boolean active,

        @NotBlank(message = "Admin full name is required")
        String adminFullName,

        @NotBlank(message = "Admin username is required")
        String adminUsername,

        @NotBlank(message = "Admin password is required")
        @Size(min = 8, message = "Admin password must be at least 8 characters")
        String adminPassword
) {
}
