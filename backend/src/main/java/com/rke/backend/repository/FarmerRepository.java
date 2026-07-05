package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rke.backend.domain.Farmer;

public interface FarmerRepository extends JpaRepository<Farmer, UUID> {

    /**
     * Flexible search by any combination of name, village and mobile. Any
     * parameter left null is ignored. Tenant scoping is applied automatically.
     */
    @Query("""
            SELECT f FROM Farmer f
            WHERE (:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:villageId IS NULL OR f.villageId = :villageId)
              AND (:mobile IS NULL OR f.mobileNumber LIKE CONCAT('%', :mobile, '%'))
            ORDER BY f.name ASC
            """)
    List<Farmer> search(@Param("name") String name,
                        @Param("villageId") UUID villageId,
                        @Param("mobile") String mobile);
}
