package com.rke.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.BillNumberType;
import com.rke.backend.domain.Farmer;
import com.rke.backend.domain.Item;
import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.TransactionItem;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.dto.SaleLineItemRequest;
import com.rke.backend.dto.SaleRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.repository.BillNumberSequenceRepository;
import com.rke.backend.repository.BillNumberTypeRepository;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.ItemRepository;
import com.rke.backend.repository.TransactionItemRepository;
import com.rke.backend.repository.TransactionRepository;
import com.rke.backend.security.CurrentUserService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class SalesServiceTest {

    private TransactionRepository transactionRepository;
    private TransactionItemRepository transactionItemRepository;
    private FarmerRepository farmerRepository;
    private ItemRepository itemRepository;
    private BillNumberTypeRepository billNumberTypeRepository;
    private BillNumberSequenceRepository billNumberSequenceRepository;
    private AuditService auditService;
    private CurrentUserService currentUserService;
    private EntityManager entityManager;

    private SalesService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FARMER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID BILL_TYPE_ID = UUID.randomUUID();
    private static final UUID GOOD_ITEM_ID = UUID.randomUUID();
    private static final UUID BAD_ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        transactionItemRepository = mock(TransactionItemRepository.class);
        farmerRepository = mock(FarmerRepository.class);
        itemRepository = mock(ItemRepository.class);
        billNumberTypeRepository = mock(BillNumberTypeRepository.class);
        billNumberSequenceRepository = mock(BillNumberSequenceRepository.class);
        auditService = mock(AuditService.class);
        currentUserService = mock(CurrentUserService.class);
        entityManager = mock(EntityManager.class);

        service = new SalesService(
                transactionRepository, transactionItemRepository,
                farmerRepository, itemRepository,
                billNumberTypeRepository, billNumberSequenceRepository,
                auditService, currentUserService, entityManager);

        when(currentUserService.getTenantId()).thenReturn(TENANT_ID);

        // Farmer exists
        Farmer farmer = Farmer.builder().tenantId(TENANT_ID).name("Test Farmer").build();
        when(farmerRepository.findById(FARMER_ID)).thenReturn(Optional.of(farmer));

        // Bill number type exists, linked to category
        BillNumberType billType = BillNumberType.builder()
                .tenantId(TENANT_ID).name("Sales").itemCategoryId(CATEGORY_ID).build();
        when(billNumberTypeRepository.findById(BILL_TYPE_ID)).thenReturn(Optional.of(billType));

        // Good item exists with both prices
        Item goodItem = Item.builder()
                .tenantId(TENANT_ID).itemCategoryId(CATEGORY_ID).name("Seed")
                .cashPrice(new BigDecimal("100.00")).creditPrice(new BigDecimal("110.00"))
                .build();
        when(itemRepository.findById(GOOD_ITEM_ID)).thenReturn(Optional.of(goodItem));

        // Bad item does NOT exist
        when(itemRepository.findById(BAD_ITEM_ID)).thenReturn(Optional.empty());

        // Auto bill number: native query returns "SALE-001"
        Query nativeQuery = mock(Query.class);
        when(entityManager.createNativeQuery(any())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn("SALE-001");

        // transactionRepository.save returns the entity it was given (with id stamped)
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            // Simulate id generation
            if (tx.getId() == null) {
                java.lang.reflect.Field idField = null;
                try {
                    idField = com.rke.backend.domain.BaseEntity.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(tx, UUID.randomUUID());
                } catch (Exception ignored) {}
            }
            return tx;
        });

        when(transactionItemRepository.save(any(TransactionItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------

    @Test
    void createSale_happyPath_cashSale() {
        SaleRequest request = new SaleRequest(
                FARMER_ID, BILL_TYPE_ID, null, LocalDate.now(),
                List.of(new SaleLineItemRequest(GOOD_ITEM_ID, new BigDecimal("2"), null)),
                "test sale");

        TransactionResponse response = service.createSale(request, TransactionType.CASH_SALE);

        assertThat(response.transactionType()).isEqualTo(TransactionType.CASH_SALE);
        assertThat(response.billNumber()).isEqualTo("SALE-001");
        assertThat(response.status()).isEqualTo(TransactionStatus.ACTIVE);
        // grand total = 2 × 100.00
        assertThat(response.grandTotal()).isEqualByComparingTo("200.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).amount()).isEqualByComparingTo("200.00");

        verify(transactionRepository, times(1)).save(any());
        verify(transactionItemRepository, times(1)).save(any());
        verify(auditService).record(eq("transactions"), eq(response.id()), eq(AuditAction.INSERT), isNull(), any());
    }

    @Test
    void createSale_usesCorrectPrice_creditSale() {
        SaleRequest request = new SaleRequest(
                FARMER_ID, BILL_TYPE_ID, null, LocalDate.now(),
                List.of(new SaleLineItemRequest(GOOD_ITEM_ID, BigDecimal.ONE, null)),
                null);

        TransactionResponse response = service.createSale(request, TransactionType.CREDIT_SALE);

        // credit price = 110.00, not cash price
        assertThat(response.grandTotal()).isEqualByComparingTo("110.00");
    }

    @Test
    void createSale_invalidItemInMiddleOfList_nothingPersisted() {
        // Three items: good, bad, good — the bad one is in the middle.
        SaleRequest request = new SaleRequest(
                FARMER_ID, BILL_TYPE_ID, null, LocalDate.now(),
                List.of(
                        new SaleLineItemRequest(GOOD_ITEM_ID, BigDecimal.ONE, null),
                        new SaleLineItemRequest(BAD_ITEM_ID, BigDecimal.ONE, null),
                        new SaleLineItemRequest(GOOD_ITEM_ID, BigDecimal.ONE, null)),
                null);

        assertThatThrownBy(() -> service.createSale(request, TransactionType.CASH_SALE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(BAD_ITEM_ID.toString());

        // Critical: nothing was written because validation runs before any save().
        verify(transactionRepository, never()).save(any());
        verify(transactionItemRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void createSale_manualBillNumber_409OnDuplicate() {
        when(transactionRepository.existsByTenantIdAndBillNumber(TENANT_ID, "DUPE-001"))
                .thenReturn(true);

        SaleRequest request = new SaleRequest(
                FARMER_ID, BILL_TYPE_ID, "DUPE-001", LocalDate.now(),
                List.of(new SaleLineItemRequest(GOOD_ITEM_ID, BigDecimal.ONE, null)),
                null);

        assertThatThrownBy(() -> service.createSale(request, TransactionType.CASH_SALE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DUPE-001");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createSale_farmerNotFound_throws404() {
        when(farmerRepository.findById(FARMER_ID)).thenReturn(Optional.empty());

        SaleRequest request = new SaleRequest(
                FARMER_ID, BILL_TYPE_ID, null, LocalDate.now(),
                List.of(new SaleLineItemRequest(GOOD_ITEM_ID, BigDecimal.ONE, null)),
                null);

        assertThatThrownBy(() -> service.createSale(request, TransactionType.CASH_SALE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(FARMER_ID.toString());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createSale_lineItemPriceOverride_usesOverride() {
        SaleRequest request = new SaleRequest(
                FARMER_ID, BILL_TYPE_ID, null, LocalDate.now(),
                List.of(new SaleLineItemRequest(GOOD_ITEM_ID, new BigDecimal("3"), new BigDecimal("50.00"))),
                null);

        TransactionResponse response = service.createSale(request, TransactionType.CASH_SALE);

        // Override price 50.00 × 3 = 150.00, not cash_price 100.00 × 3 = 300.00
        assertThat(response.grandTotal()).isEqualByComparingTo("150.00");
    }
}
