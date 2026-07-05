package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.Village;

/**
 * Village access. All queries are automatically scoped to the current tenant by
 * the Hibernate tenant filter (see TenantFilterAspect).
 */
public interface VillageRepository extends JpaRepository<Village, UUID> {

    List<Village> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<Village> findAllByOrderByNameAsc();
}
