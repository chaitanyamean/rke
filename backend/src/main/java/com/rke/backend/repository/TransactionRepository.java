package com.rke.backend.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Idempotency check — bill_number is UNIQUE per tenant (DB-enforced; this is a pre-check). */
    boolean existsByTenantIdAndBillNumber(UUID tenantId, String billNumber);

    List<Transaction> findByFarmerIdOrderByTransactionDateDesc(UUID farmerId);

    /**
     * Sum of grandTotal for a given farmer and single transaction type, scoped to
     * the current tenant by the Hibernate filter. Returns {@code null} if no rows
     * match (use {@code Optional.ofNullable(...).orElse(ZERO)} in callers).
     */
    @Query("SELECT SUM(t.grandTotal) FROM Transaction t WHERE t.farmerId = :farmerId AND t.transactionType = :type AND t.status = :status")
    BigDecimal sumGrandTotal(@Param("farmerId") UUID farmerId,
                             @Param("type") TransactionType type,
                             @Param("status") TransactionStatus status);

    /**
     * Looks up a transaction by its bill number, scoped to the current tenant by
     * the Hibernate filter. Used by ReturnService to fetch the original sale.
     */
    Optional<Transaction> findByBillNumber(String billNumber);
}
