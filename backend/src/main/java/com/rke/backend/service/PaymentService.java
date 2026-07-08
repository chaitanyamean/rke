package com.rke.backend.service;

import java.math.BigDecimal;
import java.util.List;
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
import com.rke.backend.dto.PaymentRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.repository.BillNumberTypeRepository;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.TransactionRepository;
import com.rke.backend.security.CurrentUserService;

import jakarta.persistence.EntityManager;

@Service
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final FarmerRepository farmerRepository;
    private final BillNumberTypeRepository billNumberTypeRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final EntityManager entityManager;

    public PaymentService(TransactionRepository transactionRepository,
                          FarmerRepository farmerRepository,
                          BillNumberTypeRepository billNumberTypeRepository,
                          AuditService auditService,
                          CurrentUserService currentUserService,
                          EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.farmerRepository = farmerRepository;
        this.billNumberTypeRepository = billNumberTypeRepository;
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
     * <p>outstanding = SUM(credit_sales) − SUM(cash_payments) − SUM(cash_receipts)
     * (only ACTIVE, non-voided transactions count)
     */
    @Transactional(readOnly = true)
    public BigDecimal getOutstandingBalance(UUID farmerId) {
        farmerRepository.findById(farmerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Farmer not found: " + farmerId));

        var active = TransactionStatus.ACTIVE;

        BigDecimal credit = nvl(transactionRepository.sumGrandTotal(farmerId, TransactionType.CREDIT_SALE, active));
        BigDecimal payments = nvl(transactionRepository.sumGrandTotal(farmerId, TransactionType.CASH_PAYMENT, active));
        BigDecimal receipts = nvl(transactionRepository.sumGrandTotal(farmerId, TransactionType.CASH_RECEIPT, active));

        return credit.subtract(payments).subtract(receipts);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }
}
