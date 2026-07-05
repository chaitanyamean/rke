package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.ItemCategory;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.ItemCategoryRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.ItemCategoryRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class ItemCategoryService {

    private final ItemCategoryRepository repository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public ItemCategoryService(ItemCategoryRepository repository, AuditService auditService,
                               CurrentUserService currentUserService) {
        this.repository = repository;
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
        ItemCategory category = ItemCategory.builder()
                .tenantId(currentUserService.getTenantId())
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .build();
        category = repository.save(category);
        auditService.record("item_categories", category.getId(), AuditAction.INSERT,
                null, auditService.snapshot(category));
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
