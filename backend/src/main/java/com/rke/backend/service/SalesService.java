package com.rke.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
import com.rke.backend.dto.TransactionItemResponse;
import com.rke.backend.dto.TransactionResponse;
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

        // 5. Now write: transaction header first.
        Transaction tx = Transaction.builder()
                .tenantId(tenantId)
                .farmerId(request.farmerId())
                .billNumber(billNumber)
                .billNumberTypeId(request.billNumberTypeId())
                .transactionType(type)
                .transactionDate(request.transactionDate())
                .grandTotal(grandTotal)
                .remarks(request.remarks())
                .status(TransactionStatus.ACTIVE)
                .build();

        transactionRepository.save(tx);

        // 6. Write line items.
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

        // 7. Audit.
        auditService.record("transactions", tx.getId(), AuditAction.INSERT, null,
                auditService.snapshot(tx));

        List<TransactionItemResponse> itemResponses = savedItems.stream()
                .map(TransactionItemResponse::from)
                .toList();

        return TransactionResponse.from(tx, itemResponses);
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
}
