package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.Village;

/** Village access. Villages are global master data, shared across all tenants. */
public interface VillageRepository extends JpaRepository<Village, UUID> {

    List<Village> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<Village> findAllByOrderByNameAsc();
}
