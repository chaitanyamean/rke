package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.Farmer;
import com.rke.backend.domain.Village;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.FarmerRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.VillageRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class FarmerService {

    private final FarmerRepository repository;
    private final VillageRepository villageRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public FarmerService(FarmerRepository repository, VillageRepository villageRepository,
                         AuditService auditService, CurrentUserService currentUserService) {
        this.repository = repository;
        this.villageRepository = villageRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<Farmer> search(String name, UUID villageId, String mobile) {
        return repository.search(blankToNull(name), villageId, blankToNull(mobile));
    }

    @Transactional(readOnly = true)
    public Farmer get(UUID id) {
        Farmer farmer = repository.findById(id).orElseThrow(() -> NotFoundException.of("Farmer", id));
        if (!Objects.equals(farmer.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Farmer", id);
        }
        return farmer;
    }

    @Transactional
    public Farmer create(FarmerRequest request) {
        requireVillage(request.villageId());
        Farmer farmer = Farmer.builder()
                .tenantId(currentUserService.getTenantId())
                .name(request.name().trim())
                .fatherName(trimToNull(request.fatherName()))
                .villageId(request.villageId())
                .address(trimToNull(request.address()))
                .mobileNumber(trimToNull(request.mobileNumber()))
                .reference(trimToNull(request.reference()))
                .build();
        farmer = repository.save(farmer);
        auditService.record("farmers", farmer.getId(), AuditAction.INSERT,
                null, auditService.snapshot(farmer));
        return farmer;
    }

    @Transactional
    public Farmer update(UUID id, FarmerRequest request) {
        requireVillage(request.villageId());
        Farmer farmer = get(id);
        Map<String, Object> before = auditService.snapshot(farmer);
        farmer.setName(request.name().trim());
        farmer.setFatherName(trimToNull(request.fatherName()));
        farmer.setVillageId(request.villageId());
        farmer.setAddress(trimToNull(request.address()));
        farmer.setMobileNumber(trimToNull(request.mobileNumber()));
        farmer.setReference(trimToNull(request.reference()));
        farmer = repository.save(farmer);
        auditService.record("farmers", farmer.getId(), AuditAction.UPDATE,
                before, auditService.snapshot(farmer));
        return farmer;
    }

    private void requireVillage(UUID villageId) {
        Village village = villageRepository.findById(villageId).orElse(null);
        if (village == null
                || !Objects.equals(village.getTenantId(), currentUserService.getTenantId())) {
            throw new IllegalArgumentException("Village not found: " + villageId);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
