package com.rke.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.tenant.TenantFilters;

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
 * A financial transaction (sale / payment / receipt / return).
 * bill_number is unique per tenant (see V1). Soft-delete + void via
 * {@link FinancialEntity}.
 */
@Entity
@Table(name = "transactions")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Transaction extends FinancialEntity {

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "bill_number_type_id")
    private UUID billNumberTypeId;

    // Persisted as lowercase token via TransactionTypeConverter (autoApply).
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal;

    @Column(name = "remarks")
    private String remarks;

    // Persisted as lowercase token via TransactionStatusConverter (autoApply).
    @Column(name = "status", nullable = false)
    private TransactionStatus status;
}
