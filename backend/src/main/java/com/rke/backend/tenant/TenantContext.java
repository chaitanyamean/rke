package com.rke.backend.tenant;

import java.util.UUID;

/**
 * Holds the tenant id for the current thread / request.
 *
 * <p>A later phase (auth) will populate this from the authenticated session,
 * typically in a servlet filter: {@code TenantContext.setTenantId(...)} on the
 * way in and {@link #clear()} on the way out.
 *
 * <p>When it is {@code null} (e.g. a {@code super_admin} performing cross-tenant
 * work in the Super Admin panel), the {@link TenantFilterAspect} leaves the
 * Hibernate tenant filter disabled, so queries are intentionally unscoped.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
