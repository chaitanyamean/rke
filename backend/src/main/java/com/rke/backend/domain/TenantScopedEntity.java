package com.rke.backend.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base for every tenant-owned entity. Adds the mandatory {@code tenant_id}.
 *
 * <p>Concrete subclasses additionally carry {@code @Filter(name = TenantFilters.NAME)}
 * so the tenant filter applies to them.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
}
