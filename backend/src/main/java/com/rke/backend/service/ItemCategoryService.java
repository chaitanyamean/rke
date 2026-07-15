package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.BillNumberSequence;
import com.rke.backend.domain.ItemCategory;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.ItemCategoryRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.BillNumberSequenceRepository;
import com.rke.backend.repository.ItemCategoryRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class ItemCategoryService {

    private final ItemCategoryRepository repository;
    private final BillNumberSequenceRepository billNumberSequenceRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public ItemCategoryService(ItemCategoryRepository repository,
                               BillNumberSequenceRepository billNumberSequenceRepository,
                               AuditService auditService,
                               CurrentUserService currentUserService) {
        this.repository = repository;
        this.billNumberSequenceRepository = billNumberSequenceRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<ItemCategory> search(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAllByOrderByNameAsc();
        }
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
    }

    @Transactional(readOnly = true)
    public ItemCategory get(UUID id) {
        ItemCategory category = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Item category", id));
        if (!Objects.equals(category.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Item category", id);
        }
        return category;
    }

    @Transactional
    public ItemCategory create(ItemCategoryRequest request) {
        UUID tenantId = currentUserService.getTenantId();
        ItemCategory category = ItemCategory.builder()
                .tenantId(tenantId)
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .build();
        category = repository.save(category);
        auditService.record("item_categories", category.getId(), AuditAction.INSERT,
                null, auditService.snapshot(category));

        // Provision a bill_number_sequences row for this category so bill numbers
        // can be auto-generated immediately without manual DB seeding.
        billNumberSequenceRepository.save(BillNumberSequence.builder()
                .tenantId(tenantId)
                .itemCategoryId(category.getId())
                .currentSequence(0)
                .prefix("")
                .paddingWidth(6)
                .formatTemplate("{PREFIX}{SEQ}")
                .build());

        return category;
    }

    @Transactional
    public ItemCategory update(UUID id, ItemCategoryRequest request) {
        ItemCategory category = get(id);
        Map<String, Object> before = auditService.snapshot(category);
        category.setName(request.name().trim());
        category.setDescription(trimToNull(request.description()));
        category = repository.save(category);
        auditService.record("item_categories", category.getId(), AuditAction.UPDATE,
                before, auditService.snapshot(category));
        return category;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
