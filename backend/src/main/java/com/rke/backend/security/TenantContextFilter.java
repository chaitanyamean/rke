package com.rke.backend.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.rke.backend.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bridges the authenticated principal to {@link TenantContext} for the duration
 * of the request, so the Hibernate tenant filter (enabled by
 * {@code TenantFilterAspect}) scopes queries to the logged-in user's tenant.
 *
 * <p>Runs after Spring Security has restored the security context. Always clears
 * the {@code ThreadLocal} afterwards to avoid leaking tenant state across pooled
 * request threads.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final CurrentUserService currentUserService;

    public TenantContextFilter(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            currentUserService.currentPrincipal()
                    .map(StaffUserPrincipal::getTenantId)
                    .ifPresent(TenantContext::setTenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
