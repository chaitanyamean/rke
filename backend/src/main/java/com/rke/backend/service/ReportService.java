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

        String selectSql =
                "SELECT\n" +
                "    t.id::text,\n" +
                "    t.transaction_date::text,\n" +
                "    t.bill_number,\n" +
                "    t.transaction_type,\n" +
                "    t.grand_total,\n" +
                "    " + contributionCase + " AS signed_amount,\n" +
                "    SUM(" + contributionCase + ")" +
                " OVER (ORDER BY t.transaction_date, t.created_at" +
                " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance\n" +
                "FROM transactions t\n" +
                "WHERE t.tenant_id = :tenantId\n" +
                "  AND t.farmer_id = :farmerId\n";

        StringBuilder sql = new StringBuilder(selectSql);

        if (!includeVoided) sql.append(" AND t.status = 'active'");
        if (fromDate != null) sql.append(" AND t.transaction_date >= :fromDate");
        if (toDate != null)   sql.append(" AND t.transaction_date <= :toDate");

        sql.append(" ORDER BY t.transaction_date, t.created_at");

        Query query = entityManager.createNativeQuery(sql.toString());
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
                    decimal(r[4]),   // grandTotal
                    direction,       // direction (DEBIT or CREDIT)
                    decimal(r[5]),   // signedAmount (negative for debits, positive for credits)
                    decimal(r[6]),   // runningBalance
                    BigDecimal.ZERO  // interestAmount — formula not confirmed by client (TODO)
            );
        }).toList();
    }

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
                    SUM(ti.quantity)                         AS total_quantity,
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
                    COALESCE(SUM(t.grand_total), 0)
                FROM transactions t
                WHERE t.tenant_id = :tenantId
                  AND t.transaction_type IN ('cash_sale','credit_sale')
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
                str(r[0]), decimal(r[1]), decimal(r[2]), decimal(r[3])
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
                    COALESCE(SUM(t.grand_total), 0)
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
