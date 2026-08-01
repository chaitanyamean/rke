package com.rke.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.domain.ledger.TransactionClassifier;
import com.rke.backend.dto.report.DashboardSummary;
import com.rke.backend.dto.report.DatePaymentsRow;
import com.rke.backend.dto.report.DateSalesRow;
import com.rke.backend.dto.report.TransactionReportRow;
import com.rke.backend.dto.report.FarmerOutstandingRow;
import com.rke.backend.dto.report.FarmerLedgerRow;
import com.rke.backend.dto.report.ItemSalesRow;
import com.rke.backend.dto.report.RecentTransactionRow;
import com.rke.backend.dto.report.VillageOutstandingRow;
import com.rke.backend.security.CurrentUserService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * All queries are native SQL executed against the current tenant's data.
 * Because native queries bypass the Hibernate {@code @Filter}, {@code tenant_id}
 * is always added explicitly to every WHERE clause. Aggregation is done in the
 * database — no row-by-row Java loops — so result sets scale with the data.
 *
 * <p>Dynamic SQL construction (appending WHERE clauses based on non-null params)
 * is used only with hardcoded SQL snippets; all user-supplied values are bound
 * via named parameters, so there is no SQL injection risk.
 */
@Service
public class ReportService {

    private final CurrentUserService currentUserService;
    private final EntityManager entityManager;

    public ReportService(CurrentUserService currentUserService, EntityManager entityManager) {
        this.currentUserService = currentUserService;
        this.entityManager = entityManager;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Farmer ledger — chronological history with running credit balance
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all transactions for the given farmer in chronological order with a
     * running credit balance computed as a window function in the database.
     *
     * <p>For transactions with line items (sales, returns) there is one row per
     * item so the frontend can display Category / Item / Qty / Price columns.
     * For payment/receipt transactions (no line items) a single row is returned
     * with null item-level fields.
     *
     * <p>Balance contribution per transaction type is derived from
     * {@link TransactionClassifier} — it is the single source of truth for the
     * sign convention. DEBIT types contribute a negative amount (increases what
     * the farmer owes); CREDIT types contribute a positive amount (decreases
     * what the farmer owes).
     *
     * <p>{@code interestAmount} is always {@code 0} — the real formula has not
     * been confirmed by the client yet (TODO).
     */
    @Transactional(readOnly = true)
    public List<FarmerLedgerRow> farmerLedger(UUID farmerId,
                                              LocalDate fromDate, LocalDate toDate,
                                              boolean includeVoided) {
        UUID tenantId = currentUserService.getTenantId();

        String contributionCase = buildLedgerContributionCase();

        // The running balance must be computed at transaction level (one contribution
        // per transaction) before joining item rows — otherwise a sale with N items
        // would add grand_total N times to the window sum.
        // Strategy: CTE computes per-transaction signed amount + running balance,
        // then we LEFT JOIN items back in for the line-item columns.
        String selectSql =
                "WITH tx_contrib AS (\n" +
                "    SELECT\n" +
                "        t.id,\n" +
                "        t.transaction_date,\n" +
                "        t.bill_number,\n" +
                "        t.transaction_type,\n" +
                "        t.grand_total,\n" +
                "        t.remarks,\n" +
                "        t.created_at,\n" +
                "        " + contributionCase + " AS signed_amount,\n" +
                "        CASE WHEN (" + contributionCase + ") < 0 THEN ABS(t.grand_total) ELSE 0 END AS debit_amount,\n" +
                "        CASE WHEN (" + contributionCase + ") > 0 THEN ABS(t.grand_total) ELSE 0 END AS credit_amount,\n" +
                "        NULL::numeric AS cotton_qty,\n" +
                "        NULL::numeric AS cotton_price_per_kg,\n" +
                "        NULL::uuid    AS cotton_lot_id\n" +
                "    FROM transactions t\n" +
                "    WHERE t.tenant_id = :tenantId\n" +
                "      AND t.farmer_id = :farmerId\n" +
                (includeVoided ? "" : "      AND t.status = 'active'\n") +
                (fromDate != null ? "      AND t.transaction_date >= :fromDate\n" : "") +
                (toDate   != null ? "      AND t.transaction_date <= :toDate\n"   : "") +
                "\n" +
                "    UNION ALL\n" +
                "\n" +
                "    SELECT\n" +
                "        cle.id,\n" +
                "        cl.lot_date              AS transaction_date,\n" +
                "        cl.vehicle_serial_number AS bill_number,\n" +
                "        'cotton_procurement'     AS transaction_type,\n" +
                "        (cle.quantity * cle.price) AS grand_total,\n" +
                "        NULL                     AS remarks,\n" +
                "        cl.created_at,\n" +
                "        (cle.quantity * cle.price) AS signed_amount,\n" +
                "        0                        AS debit_amount,\n" +
                "        (cle.quantity * cle.price) AS credit_amount,\n" +
                "        cle.quantity             AS cotton_qty,\n" +
                "        cle.price               AS cotton_price_per_kg,\n" +
                "        cl.id                   AS cotton_lot_id\n" +
                "    FROM cotton_lot_entries cle\n" +
                "    JOIN cotton_lots cl ON cl.id = cle.cotton_lot_id AND cl.tenant_id = :tenantId\n" +
                "    WHERE cle.tenant_id = :tenantId\n" +
                "      AND cle.farmer_id = :farmerId\n" +
                (fromDate != null ? "      AND cl.lot_date >= :fromDate\n" : "") +
                (toDate   != null ? "      AND cl.lot_date <= :toDate\n"   : "") +
                "),\n" +
                "tx_balance AS (\n" +
                "    SELECT *,\n" +
                "        SUM(signed_amount) OVER (\n" +
                "            ORDER BY transaction_date, created_at, id\n" +
                "            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW\n" +
                "        ) AS running_balance\n" +
                "    FROM tx_contrib\n" +
                ")\n" +
                "SELECT\n" +
                "    tb.id::text,\n" +
                "    tb.transaction_date::text,\n" +
                "    tb.bill_number,\n" +
                "    tb.transaction_type,\n" +
                "    ic.name   AS category_name,\n" +
                "    i.name    AS item_name,\n" +
                "    COALESCE(ti.quantity, tb.cotton_qty) AS quantity,\n" +
                "    COALESCE(ti.price, tb.cotton_price_per_kg) AS price,\n" +
                "    tb.debit_amount,\n" +
                "    tb.credit_amount,\n" +
                "    tb.running_balance,\n" +
                "    tb.remarks,\n" +
                "    tb.cotton_lot_id::text\n" +
                "FROM tx_balance tb\n" +
                "LEFT JOIN transaction_items ti ON ti.transaction_id = tb.id AND ti.tenant_id = :tenantId\n" +
                "                               AND tb.transaction_type != 'cotton_procurement'\n" +
                "LEFT JOIN items i              ON i.id = ti.item_id\n" +
                "LEFT JOIN item_categories ic   ON ic.id = i.item_category_id\n" +
                "ORDER BY tb.transaction_date, tb.created_at, tb.id, ic.name NULLS LAST, i.name NULLS LAST";

        Query query = entityManager.createNativeQuery(selectSql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("farmerId", farmerId);
        if (fromDate != null) query.setParameter("fromDate", fromDate);
        if (toDate != null)   query.setParameter("toDate", toDate);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> {
            String txType = str(r[3]);
            TransactionType type = TransactionType.valueOf(txType.toUpperCase());
            String direction = TransactionClassifier.classify(type).name();
            return new FarmerLedgerRow(
                    str(r[0]),       // transactionId
                    str(r[1]),       // transactionDate
                    str(r[2]),       // billNumber
                    txType,          // transactionType
                    direction,       // direction (DEBIT or CREDIT)
                    str(r[4]),       // categoryName (null for payment/receipt)
                    str(r[5]),       // itemName (null for payment/receipt)
                    decimal(r[6]),   // quantity (null → 0 for payment/receipt)
                    decimal(r[7]),   // price (null → 0 for payment/receipt)
                    decimal(r[8]),   // debitAmount
                    decimal(r[9]),   // creditAmount
                    decimal(r[10]),  // runningBalance
                    BigDecimal.ZERO, // interestAmount — formula not confirmed by client (TODO)
                    str(r[11]),      // remarks
                    str(r[12])       // cottonLotId (null for non-cotton rows)
            );
        }).toList();    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Village outstandings — aggregate credit balance per village
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VillageOutstandingRow> villageOutstandings(UUID villageId) {
        UUID tenantId = currentUserService.getTenantId();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    v.id::text,
                    v.name,
                    COALESCE(SUM(
                        CASE
                            WHEN t.transaction_type = 'credit_sale'                      THEN  t.grand_total
                            WHEN t.transaction_type IN ('cash_payment','cash_receipt')   THEN -t.grand_total
                            WHEN t.transaction_type = 'return'                           THEN -ABS(t.grand_total)
                            ELSE 0
                        END
                    ), 0) AS outstanding_balance
                FROM villages v
                LEFT JOIN farmers f ON f.village_id = v.id AND f.tenant_id = :tenantId
                LEFT JOIN transactions t
                       ON t.farmer_id = f.id
                      AND t.tenant_id = :tenantId
                      AND t.status    = 'active'
                WHERE 1=1
                """);

        if (villageId != null) sql.append(" AND v.id = :villageId");

        sql.append(" GROUP BY v.id, v.name ORDER BY v.name");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("tenantId", tenantId);
        if (villageId != null) query.setParameter("villageId", villageId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new VillageOutstandingRow(
                str(r[0]),
                str(r[1]),
                decimal(r[2])
        )).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2b. Farmer outstandings — per-farmer outstanding balance with date filter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns each farmer's outstanding balance for the given date range,
     * using the same {@link TransactionClassifier} sign convention as
     * {@link com.rke.backend.service.PaymentService#getOutstandingBalance}.
     * Only active (non-voided) transactions are included.
     * Optionally filtered by village.
     */
    @Transactional(readOnly = true)
    public List<FarmerOutstandingRow> farmerOutstandings(UUID villageId,
                                                          LocalDate fromDate,
                                                          LocalDate toDate) {
        UUID tenantId = currentUserService.getTenantId();

        String contributionCase = buildLedgerContributionCase();

        StringBuilder sql = new StringBuilder(
                "SELECT\n" +
                "    f.id::text,\n" +
                "    f.name,\n" +
                "    f.father_name,\n" +
                "    v.name AS village_name,\n" +
                "    COALESCE(SUM(" + contributionCase + "), 0)\n" +
                "    + COALESCE((\n" +
                "        SELECT SUM(cle.quantity * cle.price)\n" +
                "        FROM cotton_lot_entries cle\n" +
                "        JOIN cotton_lots cl ON cl.id = cle.cotton_lot_id AND cl.tenant_id = :tenantId\n" +
                "        WHERE cle.farmer_id = f.id\n" +
                "          AND cle.tenant_id = :tenantId\n" +
                (fromDate != null ? "          AND cl.lot_date >= :fromDate\n" : "") +
                (toDate   != null ? "          AND cl.lot_date <= :toDate\n"   : "") +
                "    ), 0) AS outstanding_balance\n" +
                "FROM farmers f\n" +
                "LEFT JOIN villages v ON v.id = f.village_id\n" +
                "LEFT JOIN transactions t\n" +
                "       ON t.farmer_id = f.id\n" +
                "      AND t.tenant_id = :tenantId\n" +
                "      AND t.status    = 'active'\n");

        if (fromDate != null) sql.append("      AND t.transaction_date >= :fromDate\n");
        if (toDate != null)   sql.append("      AND t.transaction_date <= :toDate\n");

        sql.append("WHERE f.tenant_id = :tenantId\n");
        if (villageId != null) sql.append("  AND f.village_id = :villageId\n");

        sql.append("GROUP BY f.id, f.name, f.father_name, v.name\n");
        sql.append("ORDER BY v.name NULLS LAST, f.name");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("tenantId", tenantId);
        if (fromDate != null)  query.setParameter("fromDate", fromDate);
        if (toDate != null)    query.setParameter("toDate", toDate);
        if (villageId != null) query.setParameter("villageId", villageId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new FarmerOutstandingRow(
                str(r[0]),    // farmerId
                str(r[1]),    // farmerName
                str(r[2]),    // fatherName
                str(r[3]),    // villageName
                decimal(r[4]) // outstandingBalance
        )).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2c. Transactions report — cross-farmer transaction list with item detail
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all transactions matching the optional filters, one row per line
     * item (or one row for payment/receipt transactions with no items).
     * Excludes voided transactions. Ordered chronologically then by bill number.
     */
    @Transactional(readOnly = true)
    public List<TransactionReportRow> transactionsReport(LocalDate fromDate,
                                                          LocalDate toDate,
                                                          UUID farmerId,
                                                          String billNumber) {
        UUID tenantId = currentUserService.getTenantId();
        String cc = buildLedgerContributionCase();

        StringBuilder sql = new StringBuilder(
                "SELECT\n" +
                "    t.id::text,\n" +
                "    t.transaction_date::text,\n" +
                "    t.bill_number,\n" +
                "    t.transaction_type,\n" +
                "    f.name          AS farmer_name,\n" +
                "    f.father_name,\n" +
                "    ic.name         AS category_name,\n" +
                "    i.name          AS item_name,\n" +
                "    ti.quantity,\n" +
                "    ti.price,\n" +
                "    CASE WHEN (" + cc + ") < 0 THEN ABS(t.grand_total) ELSE 0 END AS debit_amount,\n" +
                "    CASE WHEN (" + cc + ") > 0 THEN ABS(t.grand_total) ELSE 0 END AS credit_amount,\n" +
                "    t.remarks\n" +
                "FROM transactions t\n" +
                "JOIN farmers f ON f.id = t.farmer_id AND f.tenant_id = :tenantId\n" +
                "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id AND ti.tenant_id = :tenantId\n" +
                "LEFT JOIN items i              ON i.id = ti.item_id\n" +
                "LEFT JOIN item_categories ic   ON ic.id = i.item_category_id\n" +
                "WHERE t.tenant_id = :tenantId\n" +
                "  AND t.status = 'active'\n");

        if (fromDate != null)   sql.append("  AND t.transaction_date >= :fromDate\n");
        if (toDate != null)     sql.append("  AND t.transaction_date <= :toDate\n");
        if (farmerId != null)   sql.append("  AND t.farmer_id = :farmerId\n");
        if (billNumber != null && !billNumber.isBlank())
                                sql.append("  AND t.bill_number ILIKE :billNumber\n");

        // UNION cotton procurement entries
        sql.append("\nUNION ALL\n\n");
        sql.append(
                "SELECT\n" +
                "    cle.id::text,\n" +
                "    cl.lot_date::text        AS transaction_date,\n" +
                "    cl.vehicle_serial_number AS bill_number,\n" +
                "    'cotton_procurement'     AS transaction_type,\n" +
                "    f2.name                  AS farmer_name,\n" +
                "    f2.father_name,\n" +
                "    NULL                     AS category_name,\n" +
                "    NULL                     AS item_name,\n" +
                "    cle.quantity,\n" +
                "    cle.price,\n" +
                "    0                        AS debit_amount,\n" +
                "    (cle.quantity * cle.price) AS credit_amount,\n" +
                "    NULL                     AS remarks\n" +
                "FROM cotton_lot_entries cle\n" +
                "JOIN cotton_lots cl  ON cl.id = cle.cotton_lot_id AND cl.tenant_id = :tenantId\n" +
                "JOIN farmers f2      ON f2.id = cle.farmer_id AND f2.tenant_id = :tenantId\n" +
                "WHERE cle.tenant_id = :tenantId\n");

        if (fromDate != null)   sql.append("  AND cl.lot_date >= :fromDate\n");
        if (toDate != null)     sql.append("  AND cl.lot_date <= :toDate\n");
        if (farmerId != null)   sql.append("  AND cle.farmer_id = :farmerId\n");
        if (billNumber != null && !billNumber.isBlank())
                                sql.append("  AND cl.vehicle_serial_number ILIKE :billNumber\n");

        sql.append("\nORDER BY 2, 3, 1 NULLS LAST, 7 NULLS LAST, 8 NULLS LAST");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("tenantId", tenantId);
        if (fromDate != null)   query.setParameter("fromDate", fromDate);
        if (toDate != null)     query.setParameter("toDate", toDate);
        if (farmerId != null)   query.setParameter("farmerId", farmerId);
        if (billNumber != null && !billNumber.isBlank())
                                query.setParameter("billNumber", "%" + billNumber.trim() + "%");

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> {
            String txType = str(r[3]);
            TransactionType type = TransactionType.valueOf(txType.toUpperCase());
            String direction = TransactionClassifier.classify(type).name();
            return new TransactionReportRow(
                    str(r[0]),       // transactionId
                    str(r[1]),       // transactionDate
                    str(r[2]),       // billNumber
                    txType,          // transactionType
                    direction,       // DEBIT or CREDIT
                    str(r[4]),       // farmerName
                    str(r[5]),       // fatherName
                    str(r[6]),       // categoryName
                    str(r[7]),       // itemName
                    decimal(r[8]),   // quantity
                    decimal(r[9]),   // price
                    decimal(r[10]),  // debitAmount
                    decimal(r[11]),  // creditAmount
                    str(r[12])       // remarks
            );
        }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Item sales — net quantity/amount per item (sales minus returns)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns net quantity and amount sold per item. Returns are included with
     * their negative amounts so the totals are net (SUM naturally deducts them).
     * Filtered to transaction types that carry line items (cash_sale, credit_sale,
     * return); payment and receipt rows have no items and are excluded via the join.
     */
    @Transactional(readOnly = true)
    public List<ItemSalesRow> itemSales(LocalDate fromDate, LocalDate toDate,
                                        UUID categoryId, boolean includeVoided) {
        UUID tenantId = currentUserService.getTenantId();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    i.id::text,
                    i.name,
                    ic.id::text,
                    ic.name,
                    SUM(CASE WHEN t.transaction_type = 'return'
                             THEN -ti.quantity
                             ELSE  ti.quantity END)          AS total_quantity,
                    SUM(ti.amount)                           AS total_amount
                FROM transaction_items ti
                JOIN transactions t       ON  t.id        = ti.transaction_id
                                          AND t.tenant_id  = :tenantId
                JOIN items i              ON  i.id        = ti.item_id
                JOIN item_categories ic   ON  ic.id       = i.item_category_id
                WHERE ti.tenant_id = :tenantId
                  AND t.transaction_type IN ('cash_sale','credit_sale','return')
                """);

        if (!includeVoided) sql.append(" AND t.status = 'active'");
        if (fromDate != null)   sql.append(" AND t.transaction_date >= :fromDate");
        if (toDate != null)     sql.append(" AND t.transaction_date <= :toDate");
        if (categoryId != null) sql.append(" AND i.item_category_id = :categoryId");

        sql.append(" GROUP BY i.id, i.name, ic.id, ic.name ORDER BY ic.name, i.name");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("tenantId", tenantId);
        if (fromDate != null)   query.setParameter("fromDate", fromDate);
        if (toDate != null)     query.setParameter("toDate", toDate);
        if (categoryId != null) query.setParameter("categoryId", categoryId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new ItemSalesRow(
                str(r[0]), str(r[1]), str(r[2]), str(r[3]),
                decimal(r[4]), decimal(r[5])
        )).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Daily sales — cash vs credit breakdown by date
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DateSalesRow> dateSales(LocalDate fromDate, LocalDate toDate,
                                        boolean includeVoided) {
        UUID tenantId = currentUserService.getTenantId();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    t.transaction_date::text,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'cash_sale'   THEN t.grand_total END), 0),
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'credit_sale' THEN t.grand_total END), 0),
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'return'      THEN ABS(t.grand_total) END), 0),
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'cash_sale'   THEN t.grand_total
                                     WHEN t.transaction_type = 'credit_sale' THEN t.grand_total
                                     WHEN t.transaction_type = 'return'      THEN -ABS(t.grand_total)
                                END), 0)
                FROM transactions t
                WHERE t.tenant_id = :tenantId
                  AND t.transaction_type IN ('cash_sale','credit_sale','return')
                """);

        if (!includeVoided) sql.append(" AND t.status = 'active'");
        if (fromDate != null) sql.append(" AND t.transaction_date >= :fromDate");
        if (toDate != null)   sql.append(" AND t.transaction_date <= :toDate");

        sql.append(" GROUP BY t.transaction_date ORDER BY t.transaction_date");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("tenantId", tenantId);
        if (fromDate != null) query.setParameter("fromDate", fromDate);
        if (toDate != null)   query.setParameter("toDate", toDate);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new DateSalesRow(
                str(r[0]), decimal(r[1]), decimal(r[2]), decimal(r[3]), decimal(r[4])
        )).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Daily payments — cash_payment vs cash_receipt by date
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DatePaymentsRow> datePayments(LocalDate fromDate, LocalDate toDate,
                                              boolean includeVoided) {
        UUID tenantId = currentUserService.getTenantId();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    t.transaction_date::text,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'cash_payment' THEN t.grand_total END), 0),
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'cash_receipt' THEN t.grand_total END), 0),
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'cash_receipt' THEN t.grand_total
                                     WHEN t.transaction_type = 'cash_payment' THEN -t.grand_total
                                END), 0)
                FROM transactions t
                WHERE t.tenant_id = :tenantId
                  AND t.transaction_type IN ('cash_payment','cash_receipt')
                """);

        if (!includeVoided) sql.append(" AND t.status = 'active'");
        if (fromDate != null) sql.append(" AND t.transaction_date >= :fromDate");
        if (toDate != null)   sql.append(" AND t.transaction_date <= :toDate");

        sql.append(" GROUP BY t.transaction_date ORDER BY t.transaction_date");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("tenantId", tenantId);
        if (fromDate != null) query.setParameter("fromDate", fromDate);
        if (toDate != null)   query.setParameter("toDate", toDate);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new DatePaymentsRow(
                str(r[0]), decimal(r[1]), decimal(r[2]), decimal(r[3])
        )).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Dashboard summary — today's sales/receipts/payments + outstanding totals
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardSummary dashboardSummary() {
        UUID tenantId = currentUserService.getTenantId();

        // Today's cash/credit sales, cash received (receipts) and payments.
        Query today = entityManager.createNativeQuery("""
                SELECT
                    COALESCE(SUM(CASE WHEN transaction_type = 'cash_sale'    THEN grand_total END), 0),
                    COALESCE(SUM(CASE WHEN transaction_type = 'credit_sale'  THEN grand_total END), 0),
                    COALESCE(SUM(CASE WHEN transaction_type = 'cash_receipt' THEN grand_total END), 0),
                    COALESCE(SUM(CASE WHEN transaction_type = 'cash_payment' THEN grand_total END), 0)
                FROM transactions
                WHERE tenant_id = :tenantId
                  AND status = 'active'
                  AND transaction_date = CURRENT_DATE
                """);
        today.setParameter("tenantId", tenantId);
        Object[] t = (Object[]) today.getSingleResult();
        BigDecimal cashSales = decimal(t[0]);
        BigDecimal creditSales = decimal(t[1]);
        BigDecimal cashReceived = decimal(t[2]);
        BigDecimal payments = decimal(t[3]);

        // Per-farmer outstanding balance, then aggregate total + counts.
        Query outstanding = entityManager.createNativeQuery("""
                SELECT
                    COALESCE(SUM(bal), 0)                 AS total_outstanding,
                    COUNT(*) FILTER (WHERE bal > 0)       AS customers_with_outstanding,
                    COUNT(*)                              AS total_customers
                FROM (
                    SELECT f.id,
                        COALESCE(SUM(
                            CASE
                                WHEN t.transaction_type = 'credit_sale'                     THEN  t.grand_total
                                WHEN t.transaction_type IN ('cash_payment','cash_receipt')  THEN -t.grand_total
                                WHEN t.transaction_type = 'return'                          THEN -ABS(t.grand_total)
                                ELSE 0
                            END
                        ), 0) AS bal
                    FROM farmers f
                    LEFT JOIN transactions t
                           ON t.farmer_id = f.id
                          AND t.tenant_id = :tenantId
                          AND t.status    = 'active'
                    WHERE f.tenant_id = :tenantId
                    GROUP BY f.id
                ) per_farmer
                """);
        outstanding.setParameter("tenantId", tenantId);
        Object[] o = (Object[]) outstanding.getSingleResult();

        return new DashboardSummary(
                LocalDate.now().toString(),
                cashSales,
                creditSales,
                cashSales.add(creditSales),
                cashReceived,
                payments,
                decimal(o[0]),
                longVal(o[1]),
                longVal(o[2]));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Recent transactions — latest activity across all farmers
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RecentTransactionRow> recentTransactions(int limit) {
        UUID tenantId = currentUserService.getTenantId();

        Query query = entityManager.createNativeQuery("""
                SELECT
                    t.transaction_date::text,
                    t.transaction_type,
                    t.bill_number,
                    f.name,
                    t.grand_total
                FROM transactions t
                JOIN farmers f ON f.id = t.farmer_id AND f.tenant_id = :tenantId
                WHERE t.tenant_id = :tenantId
                  AND t.status = 'active'
                ORDER BY t.transaction_date DESC, t.created_at DESC
                LIMIT :limit
                """);
        query.setParameter("tenantId", tenantId);
        query.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new RecentTransactionRow(
                str(r[0]), str(r[1]), str(r[2]), str(r[3]), decimal(r[4])
        )).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a SQL CASE expression that maps transaction_type to its signed
     * ledger contribution. Derived from TransactionClassifier so the SQL and
     * Java balance computations can never diverge.
     *
     * Result: negative for DEBIT types (increases owed), positive for CREDIT types.
     */
    private static String buildLedgerContributionCase() {
        StringBuilder sb = new StringBuilder("CASE ");
        for (TransactionType type : TransactionType.values()) {
            String token = type.name().toLowerCase();
            if (TransactionClassifier.isDebit(type)) {
                sb.append(String.format(
                    "WHEN t.transaction_type = '%s' THEN -ABS(t.grand_total) ", token));
            } else {
                sb.append(String.format(
                    "WHEN t.transaction_type = '%s' THEN ABS(t.grand_total) ", token));
            }
        }
        sb.append("ELSE 0 END");
        return sb.toString();
    }

    private static long longVal(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(o.toString());
    }
}
