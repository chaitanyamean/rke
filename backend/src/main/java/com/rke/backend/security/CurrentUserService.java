package com.rke.backend.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.rke.backend.tenant.TenantContext;

/**
 * Convenience accessor for the currently authenticated {@link StaffUserPrincipal}.
 * Used by services to stamp {@code audit_log.changed_by} and to resolve the
 * active tenant.
 */
@Service
public class CurrentUserService {

    public Optional<StaffUserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof StaffUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public StaffUserPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(
                () -> new IllegalStateException("No authenticated user in the current context"));
    }

    /** Current user id, for audit_log.changed_by. */
    public UUID getCurrentUserId() {
        return requirePrincipal().getUserId();
    }

    /**
     * Returns the effective tenant id for the current request.
     *
     * <p>For regular tenant users this equals their {@code staff_users.tenant_id}.
     * For a super_admin with an active impersonation session it returns the
     * impersonated tenant id (set by {@link TenantContextFilter}). For a
     * super_admin without impersonation it returns {@code null} (cross-tenant
     * access; Hibernate filter disabled).
     */
    public UUID getTenantId() {
        // TenantContextFilter sets this ThreadLocal for every request.
        return TenantContext.getTenantId();
    }
}
