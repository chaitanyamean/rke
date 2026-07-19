package com.rke.backend.dto.report;

import java.math.BigDecimal;

/**
 * One row in the cross-farmer transactions report. Structure mirrors
 * FarmerLedgerRow but includes farmer name and excludes running balance,
 * interest and remarks (those belong to the ledger view).
 *
 * For transactions with line items (sales, returns) there is one row per
 * item. Transaction-level fields repeat across all item rows of the same
 * transaction (the frontend collapses them with rowspan).
 *
 * For transactions without items (payment, receipt) a single row is
 * returned with null item-level fields.
 */
public record TransactionReportRow(
        String transactionId,
        String transactionDate,
        String billNumber,
        String transactionType,
        String direction,        // "DEBIT" or "CREDIT"
        String farmerName,
        String fatherName,
        // item-level (null for payment/receipt)
        String categoryName,
        String itemName,
        BigDecimal quantity,
        BigDecimal price,
        // transaction-level amounts
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String remarks) {
}
