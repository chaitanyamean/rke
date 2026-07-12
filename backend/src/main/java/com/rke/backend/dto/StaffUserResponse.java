package com.rke.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.rke.backend.domain.StaffUser;
import com.rke.backend.domain.enums.StaffRole;

/** Public view of a staff_users row for the admin-facing staff management screen. */
public record StaffUserResponse(
        UUID id,
        UUID tenantId,
        String username,
        String fullName,
        StaffRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static StaffUserResponse from(StaffUser user) {
        return new StaffUserResponse(user.getId(), user.getTenantId(), user.getUsername(),
                user.getFullName(), user.getRole(), user.isActive(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
