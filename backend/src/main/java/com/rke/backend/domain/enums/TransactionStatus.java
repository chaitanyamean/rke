package com.rke.backend.domain.enums;

/**
 * Lifecycle status of a {@code transactions} row. Persisted as a lowercase token
 * (e.g. {@code active}) via {@code TransactionStatusConverter}.
 */
public enum TransactionStatus {
    ACTIVE,
    VOIDED
}
