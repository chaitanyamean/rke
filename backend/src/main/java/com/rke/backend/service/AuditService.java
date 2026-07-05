package com.rke.backend.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rke.backend.domain.AuditLog;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.repository.AuditLogRepository;
import com.rke.backend.security.CurrentUserService;

/**
 * Writes {@code audit_log} rows for master-data / entity changes, capturing
 * old/new field snapshots as JSON and the acting user in {@code changed_by}.
 *
 * <p>Runs with {@code Propagation.MANDATORY} so it always participates in the
 * caller's transaction — the audit row commits atomically with the change it
 * describes (or not at all).
 */
@Service
public class AuditService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        CurrentUserService currentUserService,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    /** Converts an entity to a plain field map suitable for old_values/new_values. */
    public Map<String, Object> snapshot(Object entity) {
        if (entity == null) {
            return null;
        }
        return objectMapper.convertValue(entity, MAP_TYPE);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String tableName, UUID recordId, AuditAction action,
                       Map<String, Object> oldValues, Map<String, Object> newValues) {
        AuditLog log = AuditLog.builder()
                .tenantId(currentUserService.getTenantId())
                .tableName(tableName)
                .recordId(recordId)
                .action(action)
                .changedBy(currentUserService.getCurrentUserId())
                .oldValues(oldValues)
                .newValues(newValues)
                .changedAt(Instant.now())
                .build();
        auditLogRepository.save(log);
    }
}
