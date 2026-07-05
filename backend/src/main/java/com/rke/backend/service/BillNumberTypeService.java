package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.BillNumberType;
import com.rke.backend.domain.ItemCategory;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.BillNumberTypeRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.BillNumberTypeRepository;
import com.rke.backend.repository.ItemCategoryRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class BillNumberTypeService {

    private final BillNumberTypeRepository repository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public BillNumberTypeService(BillNumberTypeRepository repository,
                                 ItemCategoryRepository itemCategoryRepository,
                                 AuditService auditService,
                                 CurrentUserService currentUserService) {
        this.repository = repository;
        this.itemCategoryRepository = itemCategoryRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<BillNumberType> search(String query, UUID categoryId) {
        if (categoryId != null) {
            return repository.findByItemCategoryIdOrderByNameAsc(categoryId);
        }
        if (query == null || query.isBlank()) {
            return repository.findAllByOrderByNameAsc();
        }
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
    }

    @Transactional(readOnly = true)
    public BillNumberType get(UUID id) {
        BillNumberType type = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Bill number type", id));
        if (!Objects.equals(type.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Bill number type", id);
        }
        return type;
    }

    @Transactional
    public BillNumberType create(BillNumberTypeRequest request) {
        requireCategory(request.itemCategoryId());
        BillNumberType type = BillNumberType.builder()
                .tenantId(currentUserService.getTenantId())
                .name(request.name().trim())
                .itemCategoryId(request.itemCategoryId())
                .description(trimToNull(request.description()))
                .build();
        type = repository.save(type);
        auditService.record("bill_number_types", type.getId(), AuditAction.INSERT,
                null, auditService.snapshot(type));
        return type;
    }

    @Transactional
    public BillNumberType update(UUID id, BillNumberTypeRequest request) {
        requireCategory(request.itemCategoryId());
        BillNumberType type = get(id);
        Map<String, Object> before = auditService.snapshot(type);
        type.setName(request.name().trim());
        type.setItemCategoryId(request.itemCategoryId());
        type.setDescription(trimToNull(request.description()));
        type = repository.save(type);
        auditService.record("bill_number_types", type.getId(), AuditAction.UPDATE,
                before, auditService.snapshot(type));
        return type;
    }

    private void requireCategory(UUID categoryId) {
        ItemCategory category = itemCategoryRepository.findById(categoryId).orElse(null);
        if (category == null
                || !Objects.equals(category.getTenantId(), currentUserService.getTenantId())) {
            throw new IllegalArgumentException("Item category not found: " + categoryId);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
