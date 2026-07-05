package com.rke.backend.domain;

import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.rke.backend.domain.enums.StaffRole;
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
 * An application login. {@code username} is unique per tenant (not globally).
 *
 * <p>{@code tenantId} is nullable: it is NULL only for {@code super_admin} users,
 * who are not scoped to any single tenant (enforced by a CHECK constraint in V1).
 * The tenant filter still applies here, so a tenant admin only sees their own
 * tenant's users; super_admin management runs with no tenant in context.
 */
@Entity
@Table(name = "staff_users")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StaffUser extends BaseEntity {

    /** NULL only for super_admin (not scoped to a tenant). */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "username", nullable = false)
    private String username;

    @ToString.Exclude
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    // Persisted as lowercase token via StaffRoleConverter (autoApply).
    @Column(name = "role", nullable = false)
    private StaffRole role;

    @Column(name = "active", nullable = false)
    private boolean active;
}
