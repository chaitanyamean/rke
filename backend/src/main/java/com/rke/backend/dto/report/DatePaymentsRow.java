package com.rke.backend.dto.report;

import java.math.BigDecimal;

public record DatePaymentsRow(
        String date,
        BigDecimal paymentsTotal,
        BigDecimal receiptsTotal,
        BigDecimal dayTotal) {
}
