package com.rke.backend.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.rke.backend.domain.enums.StaffRole;
import com.rke.backend.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Bridges the authenticated principal to {@link TenantContext} for the duration
 * of the request, so the Hibernate tenant filter scopes queries to the correct
 * tenant.
 *
 * <p>For regular users, the tenant is taken from the authenticated principal.
 * For super_admin users, the tenant is taken from the {@value #IMPERSONATED_TENANT_ATTR}
 * session attribute if set (impersonation mode); otherwise TenantContext remains
 * null, leaving the Hibernate filter disabled for intentional cross-tenant access.
 *
 * <p>Always clears the ThreadLocal on the way out to prevent leaks across
 * pooled request threads.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * HTTP session attribute key that carries the UUID of the tenant a
     * super_admin is currently impersonating.
     */
    public static final String IMPERSONATED_TENANT_ATTR = "IMPERSONATED_TENANT_ID";

    private final CurrentUserService currentUserService;

    public TenantContextFilter(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            currentUserService.currentPrincipal().ifPresent(principal -> {
                if (principal.getRole() == StaffRole.SUPER_ADMIN) {
                    // Super_admin: scope to an impersonated tenant if one is active.
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        UUID impersonated = (UUID) session.getAttribute(IMPERSONATED_TENANT_ATTR);
                        if (impersonated != null) {
                            TenantContext.setTenantId(impersonated);
                        }
                    }
                    // else: TenantContext stays null — Hibernate filter disabled, cross-tenant access.
                } else {
                    TenantContext.setTenantId(principal.getTenantId());
                }
            });
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
