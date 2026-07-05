package com.rke.backend.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.service.TenantFeatureService;
import com.rke.backend.tenant.TenantContext;

/**
 * AOP guard that enforces {@link RequiresFeature} on controller and service
 * methods. Runs before the method body, checks the current tenant's feature
 * entitlements in {@code tenant_features}, and throws 403 if the feature is
 * absent or disabled.
 *
 * <p>A super_admin with no active tenant context (i.e., working in the Super
 * Admin panel) is exempt — their access is role-controlled, not feature-gated.
 */
@Aspect
@Component
public class FeatureGuardAspect {

    private final TenantFeatureService tenantFeatureService;

    public FeatureGuardAspect(TenantFeatureService tenantFeatureService) {
        this.tenantFeatureService = tenantFeatureService;
    }

    @Before("@annotation(requiresFeature)")
    public void checkFeature(RequiresFeature requiresFeature) {
        var tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // super_admin without an impersonated tenant — bypass feature check.
            return;
        }
        if (!tenantFeatureService.isEnabled(tenantId, requiresFeature.value())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Feature '" + requiresFeature.value() + "' is not enabled for this tenant");
        }
    }
}
