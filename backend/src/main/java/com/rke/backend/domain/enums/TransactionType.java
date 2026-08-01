package com.rke.backend.domain.enums;

/**
 * Kind of a {@code transactions} row. Persisted as the lowercase token
 * (e.g. {@code cash_sale}) via {@code TransactionTypeConverter}.
 */
public enum TransactionType {
    CASH_SALE,
    CREDIT_SALE,
    CASH_PAYMENT,
    CASH_RECEIPT,
    RETURN,
    COTTON_PROCUREMENT
}
