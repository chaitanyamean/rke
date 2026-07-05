package com.rke.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.dto.report.DatePaymentsRow;
import com.rke.backend.dto.report.DateSalesRow;
import com.rke.backend.dto.report.FarmerLedgerRow;
import com.rke.backend.dto.report.ItemSalesRow;
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
     * <p>Balance contribution per transaction type:
     * <ul>
     *   <li>{@code credit_sale}  → +grand_total (increases outstanding)</li>
     *   <li>{@code return}       → −|grand_total| (reduces outstanding)</li>
     *   <li>{@code cash_payment} → −grand_total (reduces outstanding)</li>
     *   <li>{@code cash_receipt} → −grand_total (reduces outstanding)</li>
     *   <li>{@code cash_sale}    → 0 (cash; no credit impact)</li>
     * </ul>
     *
     * <p>{@code interestAmount} is always {@code 0} — the real formula has not
     * been confirmed by the client yet (TODO).
     */
    @Transactional(readOnly = true)
    public List<FarmerLedgerRow> farmerLedger(UUID farmerId,
                                              LocalDate fromDate, LocalDate toDate,
                                              boolean includeVoided) {
        UUID tenantId = currentUserService.getTenantId();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    t.transaction_date::text,
                    t.bill_number,
                    t.transaction_type,
                    t.grand_total,
                    SUM(
                        CASE
                            WHEN t.transaction_type = 'credit_sale'                      THEN  t.grand_total
                            WHEN t.transaction_type IN ('cash_payment','cash_receipt')   THEN -t.grand_total
                            WHEN t.transaction_type = 'return'                           THEN -ABS(t.grand_total)
                            ELSE 0
                        END
                    ) OVER (ORDER BY t.transaction_date, t.created_at
                            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
                FROM transactions t
                WHERE t.tenant_id = :tenantId
                  AND t.farmer_id = :farmerId
                """);

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

        return rows.stream().map(r -> new FarmerLedgerRow(
                str(r[0]),
                str(r[1]),
                str(r[2]),
                decimal(r[3]),
                decimal(r[4]),
                BigDecimal.ZERO  // TODO: interest formula not confirmed by client
        )).toList();
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
                WHERE v.tenant_id = :tenantId
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
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

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
