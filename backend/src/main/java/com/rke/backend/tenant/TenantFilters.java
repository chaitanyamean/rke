package com.rke.backend.tenant;

/**
 * Shared names for the Hibernate tenant filter, referenced from the
 * {@code @FilterDef} (see {@code com.rke.backend.domain.package-info}), from the
 * {@code @Filter} on each tenant-scoped entity, and from {@link TenantFilterAspect}.
 */
public final class TenantFilters {

    /** Name of the Hibernate filter that adds {@code tenant_id = :tenantId}. */
    public static final String NAME = "tenantFilter";

    /** Name of the filter parameter carrying the current tenant id. */
    public static final String PARAM = "tenantId";

    private TenantFilters() {
    }
}
