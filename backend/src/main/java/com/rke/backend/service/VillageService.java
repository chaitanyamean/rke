package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.Village;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.VillageRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.VillageRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class VillageService {

    private final VillageRepository repository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public VillageService(VillageRepository repository, AuditService auditService,
                          CurrentUserService currentUserService) {
        this.repository = repository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<Village> search(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAllByOrderByNameAsc();
        }
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
    }

    @Transactional(readOnly = true)
    public Village get(UUID id) {
        Village village = repository.findById(id).orElseThrow(() -> NotFoundException.of("Village", id));
        // findById bypasses the Hibernate tenant filter, so verify ownership explicitly.
        if (!Objects.equals(village.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Village", id);
        }
        return village;
    }

    @Transactional
    public Village create(VillageRequest request) {
        Village village = Village.builder()
                .tenantId(currentUserService.getTenantId())
                .name(request.name().trim())
                .landmark(trimToNull(request.landmark()))
                .build();
        village = repository.save(village);
        auditService.record("villages", village.getId(), AuditAction.INSERT,
                null, auditService.snapshot(village));
        return village;
    }

    @Transactional
    public Village update(UUID id, VillageRequest request) {
        Village village = get(id);
        Map<String, Object> before = auditService.snapshot(village);
        village.setName(request.name().trim());
        village.setLandmark(trimToNull(request.landmark()));
        village = repository.save(village);
        auditService.record("villages", village.getId(), AuditAction.UPDATE,
                before, auditService.snapshot(village));
        return village;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
