package com.rke.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.TransactionItem;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.dto.ReturnLineItemRequest;
import com.rke.backend.dto.ReturnRequest;
import com.rke.backend.dto.TransactionItemResponse;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.repository.TransactionItemRepository;
import com.rke.backend.repository.TransactionRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class ReturnService {

    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public ReturnService(TransactionRepository transactionRepository,
                         TransactionItemRepository transactionItemRepository,
                         AuditService auditService,
                         CurrentUserService currentUserService) {
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    /**
     * Looks up the original sale by bill number and returns its full details for
     * the frontend to display before the user finalises the return quantities.
     * Only CASH_SALE and CREDIT_SALE transactions are considered returnable.
     */
    @Transactional(readOnly = true)
    public TransactionResponse getOriginal(String billNumber) {
        Transaction original = requireSale(billNumber);
        List<TransactionItemResponse> items = transactionItemRepository
                .findByTransactionId(original.getId()).stream()
                .map(TransactionItemResponse::from)
                .toList();
        return TransactionResponse.from(original, items);
    }

    /**
     * Records a return against an existing CASH_SALE or CREDIT_SALE.
     *
     * <p><b>Amount convention:</b> return line items carry <em>negative</em> amounts
     * ({@code amount = -(quantity × price)}). The quantity field itself is positive.
     * Phase 7 reports should {@code SUM(amount)} across all transaction_items for a
     * farmer to get the net figure — no {@code is_return} flag is needed because the
     * sign already encodes direction. Filtering to {@code transaction_type = 'return'}
     * or joining on {@code original_bill_number} gives the return-detail view.
     *
     * <p><b>Validation:</b> Each requested return quantity must not exceed the
     * quantity in the original transaction's corresponding line item. Cumulative
     * return validation (accounting for prior partial returns against the same bill)
     * is deferred to Phase 7.
     */
    @Transactional
    public TransactionResponse createReturn(ReturnRequest request) {
        UUID tenantId = currentUserService.getTenantId();

        // 1. Fetch and validate the original sale.
        Transaction original = requireSale(request.originalBillNumber());

        if (!original.getFarmerId().equals(request.farmerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bill number " + request.originalBillNumber()
                            + " does not belong to the selected farmer");
        }

        if (original.getStatus() != TransactionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot return against a voided transaction");
        }

        // 2. Build a map of the original line items keyed by itemId.
        Map<UUID, TransactionItem> originalItemMap = transactionItemRepository
                .findByTransactionId(original.getId())
                .stream()
                .collect(Collectors.toMap(TransactionItem::getItemId, Function.identity()));

        // 3. Validate ALL return items before writing anything.
        record ReturnLine(UUID itemId, BigDecimal quantity, BigDecimal price, BigDecimal amount) {}
        List<ReturnLine> lines = new ArrayList<>(request.items().size());

        for (ReturnLineItemRequest req : request.items()) {
            TransactionItem orig = originalItemMap.get(req.itemId());
            if (orig == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Item " + req.itemId() + " was not in original transaction "
                                + request.originalBillNumber());
            }
            if (req.quantity().compareTo(orig.getQuantity()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Return quantity " + req.quantity() + " exceeds original sold quantity "
                                + orig.getQuantity() + " for item " + req.itemId());
            }
            // amount is NEGATIVE — see Javadoc convention above.
            BigDecimal amount = req.quantity().multiply(orig.getPrice()).negate();
            lines.add(new ReturnLine(req.itemId(), req.quantity(), orig.getPrice(), amount));
        }

        BigDecimal grandTotal = lines.stream()
                .map(ReturnLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add); // negative

        // 4. Generate a unique return bill number derived from the original.
        String returnBillNumber = generateReturnBillNumber(request.originalBillNumber(), tenantId);

        // 5. Insert the return transaction. The original row is never touched.
        Transaction returnTx = Transaction.builder()
                .tenantId(tenantId)
                .farmerId(original.getFarmerId())
                .billNumber(returnBillNumber)
                .billNumberTypeId(original.getBillNumberTypeId())
                .transactionType(TransactionType.RETURN)
                .transactionDate(request.returnDate())
                .grandTotal(grandTotal)
                .remarks(request.remarks())
                .status(TransactionStatus.ACTIVE)
                .originalBillNumber(request.originalBillNumber())
                .build();

        transactionRepository.save(returnTx);

        // 6. Insert return line items (negative amounts).
        List<TransactionItem> savedItems = new ArrayList<>(lines.size());
        for (ReturnLine line : lines) {
            TransactionItem item = TransactionItem.builder()
                    .tenantId(tenantId)
                    .transactionId(returnTx.getId())
                    .itemId(line.itemId())
                    .quantity(line.quantity())
                    .price(line.price())
                    .amount(line.amount())
                    .build();
            savedItems.add(transactionItemRepository.save(item));
        }

        // 7. Audit.
        auditService.record("transactions", returnTx.getId(), AuditAction.INSERT, null,
                auditService.snapshot(returnTx));

        List<TransactionItemResponse> itemResponses = savedItems.stream()
                .map(TransactionItemResponse::from)
                .toList();
        return TransactionResponse.from(returnTx, itemResponses);
    }

    // -------------------------------------------------------------------------

    private Transaction requireSale(String billNumber) {
        Transaction tx = transactionRepository.findByBillNumber(billNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found: " + billNumber));
        if (tx.getTransactionType() != TransactionType.CASH_SALE
                && tx.getTransactionType() != TransactionType.CREDIT_SALE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only CASH_SALE or CREDIT_SALE transactions can be returned");
        }
        return tx;
    }

    /**
     * Generates a unique return bill number derived from the original bill number.
     * Pattern: {@code RTN-{originalBillNumber}} with a numeric suffix if there are
     * multiple returns against the same original.
     */
    private String generateReturnBillNumber(String originalBillNumber, UUID tenantId) {
        String base = "RTN-" + originalBillNumber;
        if (!transactionRepository.existsByTenantIdAndBillNumber(tenantId, base)) {
            return base;
        }
        for (int i = 2; i <= 99; i++) {
            String candidate = base + "-" + i;
            if (!transactionRepository.existsByTenantIdAndBillNumber(tenantId, candidate)) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }
}
