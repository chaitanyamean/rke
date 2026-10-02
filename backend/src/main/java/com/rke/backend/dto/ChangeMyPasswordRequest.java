package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for an admin changing their own password.
 * Unlike {@link StaffUserUpdateRequest} (which is for editing STAFF rows),
 * this is self-service and does not touch any other fields.
 */
public record ChangeMyPasswordRequest(

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword
) {
}
