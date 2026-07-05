package com.rke.backend.domain;

import java.util.UUID;

import org.hibernate.annotations.Filter;

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
 * Backs auto-generation of bill numbers per (tenant, category). The
 * {@code next_bill_number(uuid, uuid)} DB function increments {@code currentSequence}
 * atomically and formats it using the configurable template
 * ({@code formatTemplate} with {PREFIX} / {SEQ}, {@code prefix}, {@code paddingWidth}).
 */
@Entity
@Table(name = "bill_number_sequences")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BillNumberSequence extends TenantScopedEntity {

    @Column(name = "item_category_id", nullable = false)
    private UUID itemCategoryId;

    @Column(name = "current_sequence", nullable = false)
    private long currentSequence;

    @Column(name = "prefix", nullable = false)
    private String prefix;

    @Column(name = "padding_width", nullable = false)
    private int paddingWidth;

    @Column(name = "format_template", nullable = false)
    private String formatTemplate;
}
