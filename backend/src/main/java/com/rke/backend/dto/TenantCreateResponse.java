package com.rke.backend.dto;

/**
 * Response for the combined tenant + initial-admin creation flow. Confirms the
 * admin login was created and returns its username so the super_admin can relay
 * it to the client, but deliberately never echoes the password or its hash.
 */
public record TenantCreateResponse(
        TenantResponse tenant,
        boolean adminCreated,
        String adminUsername
) {
    public static TenantCreateResponse of(TenantResponse tenant, String adminUsername) {
        return new TenantCreateResponse(tenant, true, adminUsername);
    }
}
