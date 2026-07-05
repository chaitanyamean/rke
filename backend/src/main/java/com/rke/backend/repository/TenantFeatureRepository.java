package com.rke.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rke.backend.domain.TenantFeature;

public interface TenantFeatureRepository extends JpaRepository<TenantFeature, UUID> {

    /** All features for a tenant (Hibernate filter may narrow this further). */
    List<TenantFeature> findByTenantId(UUID tenantId);

    Optional<TenantFeature> findByTenantIdAndFeatureKey(UUID tenantId, String featureKey);

    /** Bypasses the Hibernate tenant filter — used by super_admin cross-tenant checks. */
    @Query("SELECT tf FROM TenantFeature tf WHERE tf.tenantId = :tenantId AND tf.featureKey = :key")
    Optional<TenantFeature> findUnfiltered(@Param("tenantId") UUID tenantId,
                                           @Param("key") String key);
}
