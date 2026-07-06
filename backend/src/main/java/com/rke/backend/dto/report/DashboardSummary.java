package com.rke.backend.dto.report;

import java.math.BigDecimal;

/** Aggregated figures for the dashboard landing page (current tenant). */
public record DashboardSummary(
        String date,
        BigDecimal todayCashSales,
        BigDecimal todayCreditSales,
        BigDecimal todayTotalSales,
        BigDecimal todayCashReceived,
        BigDecimal todayPayments,
        BigDecimal totalOutstanding,
        long customersWithOutstanding,
        long totalCustomers) {
}
