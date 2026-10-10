package com.rke.backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.domain.ledger.TransactionClassifier;
import com.rke.backend.dto.PaymentRequest;
import com.rke.backend.dto.PaymentUpdateRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.BillNumberTypeRepository;
import com.rke.backend.repository.CottonLotEntryRepository;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.TransactionRepository;
import com.rke.backend.security.CurrentUserService;

import jakarta.persistence.EntityManager;

@Service
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final FarmerRepository farmerRepository;
    private final BillNumberTypeRepository billNumberTypeRepository;
    private final CottonLotEntryRepository cottonLotEntryRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final EntityManager entityManager;

    public PaymentService(TransactionRepository transactionRepository,
                          FarmerRepository farmerRepository,
                          BillNumberTypeRepository billNumberTypeRepository,
                          CottonLotEntryRepository cottonLotEntryRepository,
                          AuditService auditService,
                          CurrentUserService currentUserService,
                          EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.farmerRepository = farmerRepository;
        this.billNumberTypeRepository = billNumberTypeRepository;
        this.cottonLotEntryRepository = cottonLotEntryRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.entityManager = entityManager;
    }

    /**
     * Records a cash_payment or cash_receipt against a farmer's account.
     * Bill number is mandatory and must be unique per tenant.
     */
    @Transactional
    public TransactionResponse createPayment(PaymentRequest request, TransactionType type) {
        UUID tenantId = currentUserService.getTenantId();

        farmerRepository.findById(request.farmerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Farmer not found: " + request.farmerId()));

        billNumberTypeRepository.findById(request.billNumberTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BillNumberType not found: " + request.billNumberTypeId()));

        if (transactionRepository.existsByTenantIdAndBillNumber(tenantId, request.billNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bill number already exists: " + request.billNumber());
        }

        String transactionNo = generateTransactionNo(tenantId, request.billNumber());

        Transaction tx = Transaction.builder()
                .tenantId(tenantId)
                .farmerId(request.farmerId())
                .billNumber(request.billNumber())
                .transactionNo(transactionNo)
                .billNumberTypeId(request.billNumberTypeId())
                .transactionType(type)
                .transactionDate(request.transactionDate())
                .grandTotal(request.amount())
                .remarks(request.remarks())
                .status(TransactionStatus.ACTIVE)
                .build();

        transactionRepository.save(tx);
        auditService.record("transactions", tx.getId(), AuditAction.INSERT, null,
                auditService.snapshot(tx));

        return TransactionResponse.from(tx, List.of());
    }

    /**
     * Corrects an existing payment/receipt: farmer, date, amount, and remarks.
     * Bill number and payment direction (payment vs receipt) are fixed — see
     * {@link PaymentUpdateRequest} javadoc.
     *
     * <p>Restricted to ADMIN via {@code @PreAuthorize} on {@link com.rke.backend.controller.PaymentController}.
     * The pre-edit snapshot is captured before any field is mutated so
     * {@code old_values} in the audit trail reflects the true prior state.
     */
    @Transactional
    public TransactionResponse updatePayment(UUID id, PaymentUpdateRequest request, TransactionType expectedType) {
        Transaction tx = requireOwned(id);

        if (tx.getTransactionType() != expectedType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction " + id + " is not a " + expectedType);
        }
        if (tx.getStatus() != TransactionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot edit a voided transaction");
        }

        farmerRepository.findById(request.farmerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Farmer not found: " + request.farmerId()));

        Map<String, Object> before = auditService.snapshot(tx);

        tx.setFarmerId(request.farmerId());
        tx.setTransactionDate(request.transactionDate());
        tx.setGrandTotal(request.amount());
        tx.setRemarks(request.remarks());
        tx = transactionRepository.save(tx);

        auditService.record("transactions", tx.getId(), AuditAction.UPDATE,
                before, auditService.snapshot(tx));

        return TransactionResponse.from(tx, List.of());
    }

    /** Fetches a transaction and confirms it belongs to the current tenant. */
    private Transaction requireOwned(UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Transaction", id));
        if (!Objects.equals(tx.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Transaction", id);
        }
        return tx;
    }

    /** Fetches a single payment/receipt by id — used to prefill the edit form. */
    @Transactional(readOnly = true)
    public TransactionResponse getPayment(UUID id, TransactionType expectedType) {
        Transaction tx = requireOwned(id);
        if (tx.getTransactionType() != expectedType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction " + id + " is not a " + expectedType);
        }
        return TransactionResponse.from(tx, List.of());
    }

    /**
     * Calls {@code next_transaction_no()} to atomically increment the per-tenant
     * counter and format {@code {YYYY}-{billNumber}-{increment}}.
     */
    private String generateTransactionNo(UUID tenantId, String billNumber) {
        entityManager.flush();
        entityManager.clear();

        return (String) entityManager
                .createNativeQuery("SELECT next_transaction_no(:tenantId, :billNumber)")
                .setParameter("tenantId", tenantId)
                .setParameter("billNumber", billNumber)
                .getSingleResult();
    }

    /**
     * Computes the outstanding balance for a farmer by summing directly from the
     * transaction log — never from a stored column, so it can never drift.
     *
     * <p>Uses {@link TransactionClassifier} to determine the sign of each transaction type.
     * outstanding &lt; 0 → farmer owes firm; outstanding &gt; 0 → firm owes farmer.
     * (only ACTIVE, non-voided transactions count)
     */
    @Transactional(readOnly = true)
    public BigDecimal getOutstandingBalance(UUID farmerId) {
        farmerRepository.findById(farmerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Farmer not found: " + farmerId));

        UUID tenantId = currentUserService.getTenantId();

        // Base balance: sum of all transaction types using TransactionClassifier sign convention
        BigDecimal outstanding = BigDecimal.ZERO;
        for (TransactionType type : TransactionType.values()) {
            BigDecimal total = nvl(transactionRepository.sumGrandTotal(
                    farmerId, type, TransactionStatus.ACTIVE));
            outstanding = outstanding.add(TransactionClassifier.signedAmount(type, total));
        }

        // Cotton procurement entries live in cotton_lot_entries, not transactions.
        // They are always credits (positive).
        BigDecimal cottonCredit = nvl(
                cottonLotEntryRepository.sumCottonCreditByFarmer(farmerId, tenantId));
        outstanding = outstanding.add(cottonCredit);

        // Interest: 24% per annum on negative running balance between transactions,
        // same formula as FarmerLedgerPage.tsx. Computed via native SQL window function.
        BigDecimal interest = nvl(computeInterest(farmerId, tenantId));
        // Interest increases what the farmer owes — subtract from outstanding
        // (outstanding is negative when farmer owes, so subtracting makes it more negative)
        outstanding = outstanding.subtract(interest);

        return outstanding;
    }

    /**
     * Computes total interest using the same 24%/365 formula as the Farmer Ledger page:
     * for each consecutive pair of transactions (ordered by date), if the running balance
     * before the next transaction is negative (farmer owes), interest accrues at 24% p.a.
     * Plus trailing interest from the last transaction date to today.
     */
    private BigDecimal computeInterest(UUID farmerId, UUID tenantId) {
        String contributionCase = buildLedgerContributionCase();

        String sql =
                "WITH tx AS (\n" +
                "    SELECT itx.transaction_date, itx.created_at, itx.id,\n" +
                "           " + contributionCase + " AS signed_amount\n" +
                "    FROM transactions itx\n" +
                "    WHERE itx.farmer_id = :farmerId\n" +
                "      AND itx.tenant_id = :tenantId\n" +
                "      AND itx.status    = 'active'\n" +
                "),\n" +
                "cotton AS (\n" +
                "    SELECT cl.lot_date AS transaction_date, cl.created_at, cle.id,\n" +
                "           (cle.quantity * cle.price) AS signed_amount\n" +
                "    FROM cotton_lot_entries cle\n" +
                "    JOIN cotton_lots cl ON cl.id = cle.cotton_lot_id AND cl.tenant_id = :tenantId\n" +
                "    WHERE cle.farmer_id = :farmerId AND cle.tenant_id = :tenantId\n" +
                "),\n" +
                "all_tx AS (\n" +
                "    SELECT transaction_date, created_at, id, signed_amount FROM tx\n" +
                "    UNION ALL\n" +
                "    SELECT transaction_date, created_at, id, signed_amount FROM cotton\n" +
                "),\n" +
                "running AS (\n" +
                "    SELECT transaction_date,\n" +
                "           SUM(signed_amount) OVER (\n" +
                "               ORDER BY transaction_date, created_at, id\n" +
                "               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW\n" +
                "           ) AS running_balance,\n" +
                "           LEAD(transaction_date) OVER (\n" +
                "               ORDER BY transaction_date, created_at, id\n" +
                "           ) AS next_date,\n" +
                "           ROW_NUMBER() OVER (\n" +
                "               ORDER BY transaction_date, created_at, id\n" +
                "           ) AS rn,\n" +
                "           COUNT(*) OVER () AS total_rows\n" +
                "    FROM all_tx\n" +
                "),\n" +
                "interest_rows AS (\n" +
                "    SELECT CASE WHEN running_balance < 0\n" +
                "                THEN ABS(running_balance)\n" +
                "                     * GREATEST(0, (next_date - transaction_date))\n" +
                "                     * 24.0 / 365.0 / 100.0\n" +
                "                ELSE 0 END AS interest_amt\n" +
                "    FROM running WHERE next_date IS NOT NULL\n" +
                "    UNION ALL\n" +
                "    SELECT CASE WHEN running_balance < 0\n" +
                "                THEN ABS(running_balance)\n" +
                "                     * GREATEST(0, (CURRENT_DATE - transaction_date))\n" +
                "                     * 24.0 / 365.0 / 100.0\n" +
                "                ELSE 0 END AS interest_amt\n" +
                "    FROM running WHERE rn = total_rows\n" +
                ")\n" +
                "SELECT COALESCE(SUM(interest_amt), 0) FROM interest_rows";

        jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
        query.setParameter("farmerId", farmerId);
        query.setParameter("tenantId", tenantId);
        Object result = query.getSingleResult();
        return result == null ? BigDecimal.ZERO : new BigDecimal(result.toString());
    }

    private static String buildLedgerContributionCase() {
        StringBuilder sb = new StringBuilder("CASE ");
        for (TransactionType type : TransactionType.values()) {
            String token = type.name().toLowerCase();
            if (TransactionClassifier.isDebit(type)) {
                sb.append(String.format(
                    "WHEN itx.transaction_type = '%s' THEN -ABS(itx.grand_total) ", token));
            } else {
                sb.append(String.format(
                    "WHEN itx.transaction_type = '%s' THEN ABS(itx.grand_total) ", token));
            }
        }
        sb.append("ELSE 0 END");
        return sb.toString();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }
}
