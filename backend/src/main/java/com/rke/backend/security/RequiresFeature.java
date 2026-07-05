package com.rke.backend.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or service method as requiring a specific tenant feature
 * to be enabled. Enforced by {@link FeatureGuardAspect} which reads
 * {@code tenant_features} for the current session's tenant and throws 403 if
 * the feature is absent or disabled.
 *
 * <p>Super_admin callers without an active tenant context bypass this check
 * (they are in the admin panel, not acting as a tenant).
 *
 * <p>Example:
 * <pre>{@code
 * @GetMapping("/api/cotton-lots")
 * @RequiresFeature("cotton_procurement")
 * public List<CottonLot> list() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    /** The {@code feature_key} value in {@code tenant_features}. */
    String value();
}
