package com.rke.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base for financial / transactional entities. These are NEVER hard-deleted:
 * {@code deletedAt} marks a soft delete, and {@code voidedAt} / {@code voidReason}
 * record reversals.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class FinancialEntity extends TenantScopedEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;
}
