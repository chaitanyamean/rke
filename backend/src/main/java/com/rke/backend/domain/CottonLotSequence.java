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
 * Backs auto-generation of cotton lot vehicle serial numbers per tenant.
 * One row per tenant; incremented atomically by {@code next_cotton_lot_serial()}.
 * Created automatically in {@link com.rke.backend.service.TenantService#create} so
 * new tenants can immediately create cotton lots without manual DB seeding.
 */
@Entity
@Table(name = "cotton_lot_sequences")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CottonLotSequence extends TenantScopedEntity {

    @Column(name = "current_sequence", nullable = false)
    private long currentSequence;

    @Column(name = "prefix", nullable = false)
    private String prefix;

    @Column(name = "padding_width", nullable = false)
    private int paddingWidth;

    @Column(name = "format_template", nullable = false)
    private String formatTemplate;
}
