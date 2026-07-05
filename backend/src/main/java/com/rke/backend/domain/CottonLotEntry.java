package com.rke.backend.domain;

import java.math.BigDecimal;
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

/** A per-farmer line within a {@link CottonLot}. */
@Entity
@Table(name = "cotton_lot_entries")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CottonLotEntry extends FinancialEntity {

    @Column(name = "cotton_lot_id", nullable = false)
    private UUID cottonLotId;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "village_id", nullable = false)
    private UUID villageId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
}
