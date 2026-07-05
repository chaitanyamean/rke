package com.rke.backend.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

    /** Current tenant id (null for a super_admin acting cross-tenant). */
    public UUID getTenantId() {
        return currentPrincipal().map(StaffUserPrincipal::getTenantId).orElse(null);
    }
}
