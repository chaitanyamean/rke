package com.rke.backend.dto.report;

import java.math.BigDecimal;

/**
 * One row in the farmer ledger report. For transactions with line items
 * (sales, returns), there is one row per item — transaction-level fields
 * (date, billNumber, transactionType, direction, debit, credit,
 * runningBalance, remarks) are identical across all rows of the same
 * transaction and are collapsed by the frontend using rowspan.
 *
 * For transactions without line items (cash_payment, cash_receipt) the
 * item-level fields (categoryName, itemName, quantity, price) are null.
 */
public record FarmerLedgerRow(
        String transactionId,
        String transactionDate,
        String billNumber,
        String transactionType,
        String direction,          // "DEBIT" or "CREDIT"
        // item-level fields (null for payment/receipt rows)
        String categoryName,
        String itemName,
        BigDecimal quantity,
        BigDecimal price,
        // transaction-level amounts (same value repeated for every item row)
        BigDecimal debitAmount,    // grand_total when DEBIT, else 0
        BigDecimal creditAmount,   // grand_total when CREDIT, else 0
        BigDecimal runningBalance,
        // TODO: interest formula not confirmed by client — always 0 for now.
        BigDecimal interestAmount,
        String remarks,
        // For cotton_procurement rows: the cotton_lots.id (used for edit link).
        // Null for all other transaction types.
        String cottonLotId) {
}
