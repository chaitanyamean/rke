package com.rke.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for an ADMIN editing an existing staff login within their own tenant.
 * Only {@code fullName} and {@code active} are always updated; {@code role}
 * cannot be changed through this endpoint — it stays fixed at {@code STAFF}
 * (enforced in {@code StaffUserService.update}, not just by omission here).
 *
 * <p>{@code newPassword} is optional: leave it blank/null to keep the current
 * password. When supplied it must meet the same minimum length as creation —
 * this can't be expressed with {@code @Size} alone (which would reject a blank
 * "no change" value too), so {@code StaffUserService.update} validates the
 * length manually only when a non-blank value is actually supplied.
 */
public record StaffUserUpdateRequest(

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotNull(message = "Active status is required")
        Boolean active,

        /** Optional. If blank/null, the existing password is left unchanged. */
        String newPassword
) {
}
