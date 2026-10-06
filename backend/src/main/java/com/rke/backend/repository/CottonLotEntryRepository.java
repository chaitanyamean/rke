package com.rke.backend.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rke.backend.domain.CottonLotEntry;

public interface CottonLotEntryRepository extends JpaRepository<CottonLotEntry, UUID> {

    List<CottonLotEntry> findByCottonLotId(UUID cottonLotId);

    void deleteByCottonLotId(UUID cottonLotId);

    /**
     * Sums quantity * price for all cotton lot entries belonging to a given farmer
     * within the current tenant. Cotton procurement is always a credit (positive).
     */
    @Query("""
            SELECT COALESCE(SUM(e.quantity * e.price), 0)
            FROM CottonLotEntry e
            WHERE e.farmerId = :farmerId
              AND e.tenantId = :tenantId
            """)
    BigDecimal sumCottonCreditByFarmer(@Param("farmerId") UUID farmerId,
                                       @Param("tenantId") UUID tenantId);
}
