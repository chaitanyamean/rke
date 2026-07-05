package com.rke.backend.tenant;

import java.util.UUID;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Automatically enables the Hibernate tenant filter for the current session on
 * every call into the persistence / service layer, using the tenant id from
 * {@link TenantContext}.
 *
 * <p>This is the "makes it hard to forget" mechanism: rather than remembering to
 * add {@code WHERE tenant_id = ?} to every query by hand, the filter is turned on
 * declaratively (via {@code @Filter} on the entities) and activated here for the
 * whole unit of work. Every {@code SELECT} Hibernate issues for a filtered entity
 * then carries {@code tenant_id = :tenantId} automatically.
 *
 * <p>If {@link TenantContext#getTenantId()} is {@code null} (e.g. a
 * {@code super_admin} working across tenants) the filter is left off, so those
 * flows can query across tenants deliberately.
 *
 * <p>Targets the repository layer. Because our services are {@code @Transactional},
 * any repository call already runs inside the service's open transaction, so
 * unwrapping the shared {@link EntityManager} yields the current transactional
 * {@link Session} and the filter applies to the query that follows.
 */
@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.rke.backend..repository..*(..))")
    public void enableTenantFilter() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TenantFilters.NAME).setParameter(TenantFilters.PARAM, tenantId);
    }
}
