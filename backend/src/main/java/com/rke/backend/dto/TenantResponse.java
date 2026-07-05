package com.rke.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.rke.backend.domain.Tenant;

/** Public projection of a {@link Tenant} row — safe to expose to any authenticated caller. */
public record TenantResponse(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String primaryColor,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getSlug(), t.getLogoUrl(),
                t.getPrimaryColor(), t.isActive(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
