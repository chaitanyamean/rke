/**
 * JPA entities for the RKE schema.
 *
 * <p>Defines the global Hibernate tenant filter once here. Each tenant-scoped
 * entity opts in with {@code @Filter(name = TenantFilters.NAME)}; the filter is
 * activated per unit of work by
 * {@link com.rke.backend.tenant.TenantFilterAspect}.
 */
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = UUID.class),
        defaultCondition = "tenant_id = :tenantId"
)
package com.rke.backend.domain;

import java.util.UUID;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
