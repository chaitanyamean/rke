package com.rke.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rke.backend.domain.BillNumberSequence;

public interface BillNumberSequenceRepository extends JpaRepository<BillNumberSequence, UUID> {

    /**
     * Finds the sequence for the current tenant's category. Works correctly
     * both with the Hibernate tenant filter enabled (tenant user) and with
     * an explicit tenantId parameter (preview from service layer).
     */
    Optional<BillNumberSequence> findByItemCategoryId(UUID itemCategoryId);

    /** Bypasses Hibernate filter — used in the peek/preview path by the service. */
    @Query("SELECT s FROM BillNumberSequence s WHERE s.tenantId = :tenantId AND s.itemCategoryId = :categoryId")
    Optional<BillNumberSequence> findByTenantAndCategory(@Param("tenantId") UUID tenantId,
                                                          @Param("categoryId") UUID categoryId);
}
