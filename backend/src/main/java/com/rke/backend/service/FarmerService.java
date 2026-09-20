package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.Farmer;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.FarmerRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.VillageRepository;
import com.rke.backend.security.CurrentUserService;

@Service
public class FarmerService {

    private static final Logger log = LoggerFactory.getLogger(FarmerService.class);

    private final FarmerRepository repository;
    private final VillageRepository villageRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Tracer tracer;

    public FarmerService(FarmerRepository repository, VillageRepository villageRepository,
                         AuditService auditService, CurrentUserService currentUserService,
                         Tracer tracer) {
        this.repository = repository;
        this.villageRepository = villageRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.tracer = tracer;
    }

    @Transactional(readOnly = true)
    public List<Farmer> search(String name, String fatherName, UUID villageId, String mobile) {
        return repository.search(blankToNull(name), blankToNull(fatherName), villageId, blankToNull(mobile));
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
        Span span = tracer.nextSpan().name("farmer.create").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("farmer.village_id", request.villageId().toString());
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
            span.tag("farmer.id", farmer.getId().toString());
            auditService.record("farmers", farmer.getId(), AuditAction.INSERT,
                    null, auditService.snapshot(farmer));
            log.info("Farmer created: id={}", farmer.getId());
            return farmer;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional
    public Farmer update(UUID id, FarmerRequest request) {
        requireVillage(request.villageId());
        Span span = tracer.nextSpan().name("farmer.update").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("farmer.id", id.toString());
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
            log.info("Farmer updated: id={}", farmer.getId());
            return farmer;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private void requireVillage(UUID villageId) {
        if (!villageRepository.existsById(villageId)) {
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
