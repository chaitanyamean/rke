package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.TransactionItem;

public interface TransactionItemRepository extends JpaRepository<TransactionItem, UUID> {

    List<TransactionItem> findByTransactionId(UUID transactionId);

    /** Line items across several transactions at once — used to sum prior returns. */
    List<TransactionItem> findByTransactionIdIn(List<UUID> transactionIds);
}
