package com.rke.backend.dto.report;

import java.math.BigDecimal;

public record FarmerLedgerRow(
        String transactionId,
        String transactionDate,
        String billNumber,
        String transactionType,
        BigDecimal grandTotal,
        BigDecimal runningBalance,
        // TODO: interest formula not confirmed by client — always 0 for now.
        BigDecimal interestAmount) {
}
