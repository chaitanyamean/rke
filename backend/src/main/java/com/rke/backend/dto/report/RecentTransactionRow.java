package com.rke.backend.dto.report;

import java.math.BigDecimal;

/** A single row for the dashboard "Recent Transactions" table. */
public record RecentTransactionRow(
        String date,
        String type,        // e.g. cash_sale, credit_sale, cash_receipt, cash_payment, return
        String billNumber,
        String farmerName,
        BigDecimal amount) {
}
