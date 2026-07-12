package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.TransactionItem;

public interface TransactionItemRepository extends JpaRepository<TransactionItem, UUID> {

    List<TransactionItem> findByTransactionId(UUID transactionId);

    /** Line items across several transactions at once — used to sum prior returns. */
    List<TransactionItem> findByTransactionIdIn(List<UUID> transactionIds);

    /**
     * Removes all line items for a transaction. Used when editing a sale/return
     * — the existing lines are replaced wholesale with the corrected set rather
     * than diffed, which keeps the edit logic simple and matches how the create
     * path builds lines in the first place.
     */
    void deleteByTransactionId(UUID transactionId);
}
