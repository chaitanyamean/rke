package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for an ADMIN creating a new staff login within their own tenant.
 * Role is deliberately not a field here — it is always locked to {@code STAFF}
 * by {@code StaffUserService.create}; an admin cannot create another admin or a
 * super_admin through this endpoint.
 */
public record StaffUserCreateRequest(

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}
