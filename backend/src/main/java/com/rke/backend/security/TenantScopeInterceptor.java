package com.rke.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.rke.backend.domain.enums.StaffRole;
import com.rke.backend.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Defense-in-depth interceptor that enforces tenant isolation at the HTTP layer,
 * complementing the Hibernate-level tenant filter applied by
 * {@link com.rke.backend.tenant.TenantFilterAspect}.
 *
 * <p>Rules applied on every {@code /api/**} request (after auth endpoints are
 * excluded by the registry configuration in {@link com.rke.backend.config.WebMvcConfig}):
 * <ol>
 *   <li>{@code /api/admin/**} is strictly for {@code super_admin}: any other
 *       authenticated role is rejected with 403 before the handler is even invoked.</li>
 *   <li>Any endpoint that is NOT an admin or feature-query path requires a
 *       non-null tenant context. If a non-super_admin somehow lacks a tenant
 *       context (should not happen; indicates a broken auth flow), or a
 *       super_admin tries to hit tenant-scoped endpoints without impersonating
 *       a tenant first, the request is rejected with 403.</li>
 * </ol>
 */
@Component
public class TenantScopeInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;

    public TenantScopeInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        var principalOpt = currentUserService.currentPrincipal();
        if (principalOpt.isEmpty()) {
            // Spring Security handles unauthenticated requests before this runs.
            return true;
        }

        StaffUserPrincipal principal = principalOpt.get();
        String path = request.getRequestURI();

        // Rule 1: /api/admin/** requires super_admin role.
        if (path.startsWith("/api/admin/") && principal.getRole() != StaffRole.SUPER_ADMIN) {
            forbidden(response, "Super admin access required");
            return false;
        }

        // Rule 2: tenant-scoped endpoints require a tenant context.
        // Exempt: /api/admin/** and /api/features/mine (returns [] for unimpersonated super_admin).
        boolean exemptFromTenantContext =
                path.startsWith("/api/admin/") || path.equals("/api/features/mine");

        if (!exemptFromTenantContext && TenantContext.getTenantId() == null) {
            String hint = principal.getRole() == StaffRole.SUPER_ADMIN
                    ? "Impersonate a tenant first via POST /api/admin/tenants/{id}/impersonate"
                    : "No tenant context — contact support";
            forbidden(response, "Tenant context required. " + hint);
            return false;
        }

        return true;
    }

    private static void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\""
                + message + "\"}");
    }
}
