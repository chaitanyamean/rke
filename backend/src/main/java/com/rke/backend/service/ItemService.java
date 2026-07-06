package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.Item;
import com.rke.backend.domain.ItemCategory;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.ItemRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.ItemCategoryRepository;
import com.rke.backend.repository.ItemRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class ItemService {

    private final ItemRepository repository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public ItemService(ItemRepository repository, ItemCategoryRepository itemCategoryRepository,
                       AuditService auditService, CurrentUserService currentUserService) {
        this.repository = repository;
        this.itemCategoryRepository = itemCategoryRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<Item> list(UUID categoryId) {
        if (categoryId != null) {
            return repository.findByItemCategoryIdOrderByNameAsc(categoryId);
        }
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Item get(UUID id) {
        Item item = repository.findById(id).orElseThrow(() -> NotFoundException.of("Item", id));
        if (!Objects.equals(item.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Item", id);
        }
        return item;
    }

    @Transactional
    public Item create(ItemRequest request) {
        requireCategory(request.itemCategoryId());
        Item item = Item.builder()
                .tenantId(currentUserService.getTenantId())
                .itemCategoryId(request.itemCategoryId())
                .name(request.name().trim())
                .creditPrice(request.creditPrice())
                .cashPrice(request.cashPrice())
                .unit(request.unit())
                .build();
        item = repository.save(item);
        auditService.record("items", item.getId(), AuditAction.INSERT,
                null, auditService.snapshot(item));
        return item;
    }

    @Transactional
    public Item update(UUID id, ItemRequest request) {
        requireCategory(request.itemCategoryId());
        Item item = get(id);
        Map<String, Object> before = auditService.snapshot(item);
        item.setItemCategoryId(request.itemCategoryId());
        item.setName(request.name().trim());
        item.setCreditPrice(request.creditPrice());
        item.setCashPrice(request.cashPrice());
        item.setUnit(request.unit());
        item = repository.save(item);
        auditService.record("items", item.getId(), AuditAction.UPDATE,
                before, auditService.snapshot(item));
        return item;
    }

    private void requireCategory(UUID categoryId) {
        ItemCategory category = itemCategoryRepository.findById(categoryId).orElse(null);
        if (category == null
                || !Objects.equals(category.getTenantId(), currentUserService.getTenantId())) {
            throw new IllegalArgumentException("Item category not found: " + categoryId);
        }
    }
}
