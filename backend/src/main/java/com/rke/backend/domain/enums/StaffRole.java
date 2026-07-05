package com.rke.backend.domain.enums;

/**
 * Role of a {@code staff_users} row. Persisted as a lowercase token
 * (e.g. {@code super_admin}) via {@code StaffRoleConverter}.
 *
 * <p>{@code SUPER_ADMIN} is not scoped to any single tenant and can access the
 * cross-tenant Super Admin panel.
 */
public enum StaffRole {
    SUPER_ADMIN,
    ADMIN,
    STAFF
}
