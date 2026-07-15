package com.rke.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Backs auto-generation of human-readable transaction numbers per tenant.
 * One row per tenant; incremented atomically by {@code next_transaction_no()}.
 * Created automatically in {@link com.rke.backend.service.TenantService#create} so
 * new tenants can immediately record transactions without manual DB seeding.
 */
@Entity
@Table(name = "transaction_no_sequences")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TransactionNoSequence extends TenantScopedEntity {

    @Column(name = "current_sequence", nullable = false)
    private long currentSequence;
}
