package com.rke.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.BillNumberSequence;
import com.rke.backend.domain.BillNumberType;
import com.rke.backend.domain.Item;
import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.TransactionItem;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.dto.SaleLineItemRequest;
import com.rke.backend.dto.SaleRequest;
import com.rke.backend.dto.SaleUpdateRequest;
import com.rke.backend.dto.TransactionItemResponse;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.BillNumberSequenceRepository;
import com.rke.backend.repository.BillNumberTypeRepository;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.ItemRepository;
import com.rke.backend.repository.TransactionItemRepository;
import com.rke.backend.repository.TransactionRepository;
import com.rke.backend.security.CurrentUserService;

import jakarta.persistence.EntityManager;

@Service
public class SalesService {

    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final FarmerRepository farmerRepository;
    private final ItemRepository itemRepository;
    private final BillNumberTypeRepository billNumberTypeRepository;
    private final BillNumberSequenceRepository billNumberSequenceRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final EntityManager entityManager;

    public SalesService(TransactionRepository transactionRepository,
                        TransactionItemRepository transactionItemRepository,
                        FarmerRepository farmerRepository,
                        ItemRepository itemRepository,
                        BillNumberTypeRepository billNumberTypeRepository,
                        BillNumberSequenceRepository billNumberSequenceRepository,
                        AuditService auditService,
                        CurrentUserService currentUserService,
                        EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.farmerRepository = farmerRepository;
        this.itemRepository = itemRepository;
        this.billNumberTypeRepository = billNumberTypeRepository;
        this.billNumberSequenceRepository = billNumberSequenceRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.entityManager = entityManager;
    }

    /**
     * Creates a cash or credit sale inside a single transaction.
     *
     * <p>All inputs are fully validated before any DB write — a failure mid-list
     * still rolls back cleanly because nothing is written until after validation.
     */
    @Transactional
    public TransactionResponse createSale(SaleRequest request, TransactionType type) {
        UUID tenantId = currentUserService.getTenantId();

        // 1. Validate farmer exists (Hibernate filter scopes to tenant automatically).
        farmerRepository.findById(request.farmerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Farmer not found: " + request.farmerId()));

        // 2. Validate bill number type and resolve the item category it belongs to.
        BillNumberType billNumberType = billNumberTypeRepository.findById(request.billNumberTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BillNumberType not found: " + request.billNumberTypeId()));

        // 3. Validate ALL items and compute line amounts — before any write.
        List<PricedLine> lines = validateAndPriceLines(request.items(), type);

        BigDecimal grandTotal = lines.stream()
                .map(PricedLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Resolve bill number (auto or manual) — raises 409 on duplicate.
        String billNumber = resolveBillNumber(
                request.billNumber(), tenantId, billNumberType.getItemCategoryId());

        // 5. Generate the human-readable transaction number: {YYYY}-{billNumber}-{increment}.
        String transactionNo = generateTransactionNo(tenantId, billNumber);

        // 6. Now write: transaction header first.
        Transaction tx = Transaction.builder()
                .tenantId(tenantId)
                .farmerId(request.farmerId())
                .billNumber(billNumber)
                .transactionNo(transactionNo)
                .billNumberTypeId(request.billNumberTypeId())
                .transactionType(type)
                .transactionDate(request.transactionDate())
                .grandTotal(grandTotal)
                .remarks(request.remarks())
                .status(TransactionStatus.ACTIVE)
                .build();

        transactionRepository.save(tx);

        // 7. Write line items.
        List<TransactionItem> savedItems = new ArrayList<>(lines.size());
        for (PricedLine line : lines) {
            TransactionItem item = TransactionItem.builder()
                    .tenantId(tenantId)
                    .transactionId(tx.getId())
                    .itemId(line.itemId())
                    .quantity(line.quantity())
                    .price(line.price())
                    .amount(line.amount())
                    .build();
            savedItems.add(transactionItemRepository.save(item));
        }

        // 8. Audit.
        auditService.record("transactions", tx.getId(), AuditAction.INSERT, null,
                auditService.snapshot(tx));

        List<TransactionItemResponse> itemResponses = savedItems.stream()
                .map(TransactionItemResponse::from)
                .toList();

        return TransactionResponse.from(tx, itemResponses);
    }

    /**
     * Corrects an existing cash/credit sale: farmer, date, line items, and
     * remarks. Bill number and sale type are fixed (see {@link SaleUpdateRequest}
     * javadoc) — this is a correction path, not a way to convert one kind of
     * transaction into another.
     *
     * <p>Restricted to ADMIN via {@code @PreAuthorize} on {@link com.rke.backend.controller.SaleController}.
     *
     * <p>Line items are replaced wholesale (delete + re-insert) rather than
     * diffed, mirroring how {@link #createSale} builds them. The pre-edit
     * transaction snapshot — including its original line items — is captured in
     * {@code old_values} before anything is touched, so the audit trail always
     * has the true prior state, not just the header fields.
     *
     * <p>A voided sale cannot be edited; void first requires un-voiding (not
     * supported) or the correction should be made as a fresh transaction.
     */
    @Transactional
    public TransactionResponse updateSale(UUID id, SaleUpdateRequest request, TransactionType expectedType) {
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

        List<PricedLine> lines = validateAndPriceLines(request.items(), expectedType);
        BigDecimal grandTotal = lines.stream()
                .map(PricedLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Snapshot the full prior state — header + line items — before mutating.
        Map<String, Object> before = auditService.snapshot(tx);
        List<TransactionItem> priorItems = transactionItemRepository.findByTransactionId(tx.getId());
        before.put("items", priorItems.stream().map(TransactionItemResponse::from).toList());

        tx.setFarmerId(request.farmerId());
        tx.setTransactionDate(request.transactionDate());
        tx.setGrandTotal(grandTotal);
        tx.setRemarks(request.remarks());
        tx = transactionRepository.save(tx);

        transactionItemRepository.deleteByTransactionId(tx.getId());
        List<TransactionItem> savedItems = new ArrayList<>(lines.size());
        for (PricedLine line : lines) {
            TransactionItem item = TransactionItem.builder()
                    .tenantId(tx.getTenantId())
                    .transactionId(tx.getId())
                    .itemId(line.itemId())
                    .quantity(line.quantity())
                    .price(line.price())
                    .amount(line.amount())
                    .build();
            savedItems.add(transactionItemRepository.save(item));
        }

        Map<String, Object> after = auditService.snapshot(tx);
        after.put("items", savedItems.stream().map(TransactionItemResponse::from).toList());
        auditService.record("transactions", tx.getId(), AuditAction.UPDATE, before, after);

        List<TransactionItemResponse> itemResponses = savedItems.stream()
                .map(TransactionItemResponse::from)
                .toList();
        return TransactionResponse.from(tx, itemResponses);
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

    /** Fetches a single cash/credit sale by id — used to prefill the edit form. */
    @Transactional(readOnly = true)
    public TransactionResponse getSale(UUID id, TransactionType expectedType) {
        Transaction tx = requireOwned(id);
        if (tx.getTransactionType() != expectedType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction " + id + " is not a " + expectedType);
        }
        List<TransactionItemResponse> items = transactionItemRepository
                .findByTransactionId(tx.getId()).stream()
                .map(TransactionItemResponse::from)
                .toList();
        return TransactionResponse.from(tx, items);
    }

    /**
     * Returns what the next auto-generated bill number would be for the given
     * category without incrementing the sequence. Used for frontend preview/auto-fill.
     */
    public String previewBillNumber(UUID itemCategoryId) {
        UUID tenantId = currentUserService.getTenantId();
        BillNumberSequence seq = billNumberSequenceRepository
                .findByTenantAndCategory(tenantId, itemCategoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No bill number sequence configured for this category"));

        long nextSeq = seq.getCurrentSequence() + 1;
        String padded = String.format("%0" + seq.getPaddingWidth() + "d", nextSeq);
        return seq.getFormatTemplate()
                .replace("{PREFIX}", seq.getPrefix())
                .replace("{SEQ}", padded);
    }

    // -------------------------------------------------------------------------

    private record PricedLine(UUID itemId, BigDecimal quantity, BigDecimal price, BigDecimal amount) {}

    /**
     * Validates that every item in the list exists (tenant-scoped), resolves
     * price from master data based on sale type, and computes the line amount.
     * Fails fast on first invalid item — but iterates the full list so multi-item
     * requests surface all problems at once (accumulated into a single 404).
     */
    private List<PricedLine> validateAndPriceLines(List<SaleLineItemRequest> lineRequests,
                                                    TransactionType type) {
        // Reject duplicate items in the same sale.
        Set<UUID> seen = new HashSet<>();
        for (SaleLineItemRequest req : lineRequests) {
            if (!seen.add(req.itemId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate item in sale: " + req.itemId());
            }
        }

        List<UUID> missing = new ArrayList<>();
        List<PricedLine> result = new ArrayList<>(lineRequests.size());

        for (SaleLineItemRequest req : lineRequests) {
            itemRepository.findById(req.itemId()).ifPresentOrElse(item -> {
                BigDecimal price = (req.price() != null)
                        ? req.price()
                        : resolveDefaultPrice(item, type);
                BigDecimal amount = price.multiply(req.quantity());
                result.add(new PricedLine(item.getId(), req.quantity(), price, amount));
            }, () -> missing.add(req.itemId()));
        }

        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Items not found: " + missing);
        }

        return result;
    }

    private BigDecimal resolveDefaultPrice(Item item, TransactionType type) {
        return switch (type) {
            case CASH_SALE -> item.getCashPrice();
            case CREDIT_SALE -> item.getCreditPrice();
            default -> throw new IllegalArgumentException("Unsupported sale type: " + type);
        };
    }

    /**
     * If {@code requested} is blank, calls {@code next_bill_number()} to atomically
     * increment the sequence. If provided manually, checks uniqueness and throws
     * 409 on conflict.
     *
     * <p>The native query uses an explicit flush + clear so the EntityManager
     * doesn't interfere with the row lock taken by the Postgres function.
     */
    private String resolveBillNumber(String requested, UUID tenantId, UUID itemCategoryId) {
        if (requested != null && !requested.isBlank()) {
            if (transactionRepository.existsByTenantIdAndBillNumber(tenantId, requested)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bill number already exists: " + requested);
            }
            return requested;
        }

        entityManager.flush();
        entityManager.clear();

        String generated = (String) entityManager
                .createNativeQuery("SELECT next_bill_number(:tenantId, :categoryId)")
                .setParameter("tenantId", tenantId)
                .setParameter("categoryId", itemCategoryId)
                .getSingleResult();

        return generated;
    }

    /**
     * Calls {@code next_transaction_no()} to atomically increment the per-tenant
     * counter and format {@code {YYYY}-{billNumber}-{increment}}. Same flush +
     * clear treatment as {@link #resolveBillNumber} so the EntityManager doesn't
     * interfere with the row lock taken by the Postgres function.
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
}
