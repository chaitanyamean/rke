package com.rke.backend.dto.report;

import java.math.BigDecimal;

public record DateSalesRow(
        String date,
        BigDecimal cashSalesTotal,
        BigDecimal creditSalesTotal,
        BigDecimal dayTotal) {
}
