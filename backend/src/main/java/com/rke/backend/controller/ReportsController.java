package com.rke.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.dto.report.FarmerOutstandingRow;
import com.rke.backend.dto.report.DashboardSummary;
import com.rke.backend.dto.report.DatePaymentsRow;
import com.rke.backend.dto.report.DateSalesRow;
import com.rke.backend.dto.report.FarmerLedgerRow;
import com.rke.backend.dto.report.ItemSalesRow;
import com.rke.backend.dto.report.RecentTransactionRow;
import com.rke.backend.dto.report.VillageOutstandingRow;
import com.rke.backend.service.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Aggregated figures for the dashboard landing page. */
    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return reportService.dashboardSummary();
    }

    /** Latest transactions across all farmers (for the dashboard table). */
    @GetMapping("/recent-transactions")
    public List<RecentTransactionRow> recentTransactions(
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.recentTransactions(Math.min(Math.max(limit, 1), 50));
    }

    /**
     * Chronological transaction history for a single farmer with running balance.
     * {@code interestAmount} is always 0 — formula not confirmed by client yet.
     */
    @GetMapping("/farmer-ledger/{farmerId}")
    public List<FarmerLedgerRow> farmerLedger(
            @PathVariable UUID farmerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return reportService.farmerLedger(farmerId, fromDate, toDate, includeVoided);
    }

    /** Outstanding credit balance aggregated per village. Excludes voided transactions. */
    @GetMapping("/village-outstandings")
    public List<VillageOutstandingRow> villageOutstandings(
            @RequestParam(required = false) UUID villageId) {
        return reportService.villageOutstandings(villageId);
    }

    /** Outstanding balance per farmer, filtered by date range and optional village. */
    @GetMapping("/farmer-outstandings")
    public List<FarmerOutstandingRow> farmerOutstandings(
            @RequestParam(required = false) UUID villageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return reportService.farmerOutstandings(villageId, fromDate, toDate);
    }

    /** Net quantity and amount sold (sales minus returns) per item. */
    @GetMapping("/item-sales")
    public List<ItemSalesRow> itemSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return reportService.itemSales(fromDate, toDate, categoryId, includeVoided);
    }

    /** Daily sales totals with cash vs credit breakdown. */
    @GetMapping("/date-sales")
    public List<DateSalesRow> dateSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return reportService.dateSales(fromDate, toDate, includeVoided);
    }

    /** Daily payment and receipt totals. */
    @GetMapping("/date-payments")
    public List<DatePaymentsRow> datePayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return reportService.datePayments(fromDate, toDate, includeVoided);
    }
}
