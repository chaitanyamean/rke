package com.rke.backend.domain;

import org.hibernate.annotations.Filter;

import com.rke.backend.tenant.TenantFilters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Per-tenant feature entitlement. A {@code featureKey} absent for a tenant is
 * treated as disabled by default, so new tenants start with nothing extra
 * enabled until a super_admin turns a feature on.
 */
@Entity
@Table(name = "tenant_features")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TenantFeature extends TenantScopedEntity {

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
