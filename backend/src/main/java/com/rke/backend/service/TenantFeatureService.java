package com.rke.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.TenantFeature;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.TenantFeatureRepository;
import com.rke.backend.repository.TenantRepository;
import com.rke.backend.tenant.TenantContext;

@Service
public class TenantFeatureService {

    private final TenantFeatureRepository featureRepository;
    private final TenantRepository tenantRepository;

    public TenantFeatureService(TenantFeatureRepository featureRepository,
                                TenantRepository tenantRepository) {
        this.featureRepository = featureRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Lists all feature rows for a tenant. Called by the super_admin panel;
     * the Hibernate tenant filter is OFF so we query by explicit tenant_id.
     */
    @Transactional(readOnly = true)
    public List<TenantFeature> listForTenant(UUID tenantId) {
        requireTenant(tenantId);
        return featureRepository.findByTenantId(tenantId);
    }

    /**
     * Enables or disables a feature for a tenant. Creates the row if absent
     * (upsert semantics).
     */
    @Transactional
    public TenantFeature setFeature(UUID tenantId, String featureKey, boolean enabled) {
        requireTenant(tenantId);
        TenantFeature feature = featureRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .orElseGet(() -> TenantFeature.builder()
                        .tenantId(tenantId)
                        .featureKey(featureKey)
                        .build());
        feature.setEnabled(enabled);
        return featureRepository.save(feature);
    }

    /**
     * Returns the enabled feature keys for the current request's tenant
     * (from {@link TenantContext}). Returns an empty list for a super_admin
     * without an active impersonation context.
     */
    @Transactional(readOnly = true)
    public List<String> enabledKeysForCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return List.of();
        }
        return featureRepository.findByTenantId(tenantId).stream()
                .filter(TenantFeature::isEnabled)
                .map(TenantFeature::getFeatureKey)
                .toList();
    }

    /**
     * Checks whether a specific feature is enabled for {@code tenantId}.
     * Uses the unfiltered query so this works for both tenant users and
     * super_admin impersonation flows.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String featureKey) {
        return featureRepository.findUnfiltered(tenantId, featureKey)
                .map(TenantFeature::isEnabled)
                .orElse(false);
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw NotFoundException.of("Tenant", tenantId);
        }
    }
}
