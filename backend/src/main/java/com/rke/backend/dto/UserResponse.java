package com.rke.backend.dto;

import java.util.UUID;

import com.rke.backend.domain.StaffUser;
import com.rke.backend.domain.enums.StaffRole;

/** Public view of the authenticated user (never exposes the password hash). */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String username,
        String fullName,
        StaffRole role
) {
    public static UserResponse from(StaffUser user) {
        return new UserResponse(user.getId(), user.getTenantId(), user.getUsername(),
                user.getFullName(), user.getRole());
    }

    public static UserResponse from(com.rke.backend.security.StaffUserPrincipal principal) {
        return new UserResponse(principal.getUserId(), principal.getTenantId(),
                principal.getUsername(), principal.getFullName(), principal.getRole());
    }
}
