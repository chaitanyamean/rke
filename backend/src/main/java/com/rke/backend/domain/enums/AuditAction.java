package com.rke.backend.domain.enums;

/**
 * Action recorded in {@code audit_log}. Persisted as a lowercase token
 * (e.g. {@code insert}) via {@code AuditActionConverter}.
 */
public enum AuditAction {
    INSERT,
    UPDATE,
    VOID
}
