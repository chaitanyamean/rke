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

/** Catalog item, priced for both credit and cash sales. */
@Entity
@Table(name = "items")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Item extends TenantScopedEntity {

    @Column(name = "item_category_id", nullable = false)
    private UUID itemCategoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "credit_price", nullable = false)
    private BigDecimal creditPrice;

    @Column(name = "cash_price", nullable = false)
    private BigDecimal cashPrice;
}
