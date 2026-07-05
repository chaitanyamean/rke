package com.rke.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.domain.TenantFeature;
import com.rke.backend.dto.FeatureToggleRequest;
import com.rke.backend.service.TenantFeatureService;

@RestController
public class TenantFeatureController {

    private final TenantFeatureService featureService;

    public TenantFeatureController(TenantFeatureService featureService) {
        this.featureService = featureService;
    }

    /** List all feature rows for a tenant (super_admin only). */
    @GetMapping("/api/admin/tenants/{id}/features")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<TenantFeature> listFeatures(@PathVariable UUID id) {
        return featureService.listForTenant(id);
    }

    /** Enable or disable a feature key for a tenant (super_admin only). */
    @PutMapping("/api/admin/tenants/{id}/features/{featureKey}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TenantFeature setFeature(@PathVariable UUID id,
                                     @PathVariable String featureKey,
                                     @RequestBody FeatureToggleRequest request) {
        return featureService.setFeature(id, featureKey, request.enabled());
    }

    /**
     * Returns the enabled feature keys for the current session's tenant.
     * Any authenticated user may call this — the frontend uses it to decide
     * which nav items and routes to show. Returns {@code []} for super_admin
     * without an active impersonation.
     */
    @GetMapping("/api/features/mine")
    public List<String> myFeatures() {
        return featureService.enabledKeysForCurrentTenant();
    }
}
