package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.StaffUser;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    /**
     * Look up by username. Returns a list because usernames are unique only per
     * tenant (two tenants can each have a "raj"). During login the tenant filter
     * is off (no tenant in context yet), so this may return matches across
     * tenants; the caller disambiguates.
     */
    List<StaffUser> findByUsername(String username);

    /**
     * Lists staff users for the admin-facing staff management screen. Relies on
     * the Hibernate tenant filter (enabled automatically for the current
     * request's tenant) to scope results — a bare {@code tenant_id = :tenantId}
     * condition also naturally excludes super_admin rows, whose tenant_id is
     * always NULL.
     */
    List<StaffUser> findAllByOrderByUsernameAsc();
}
