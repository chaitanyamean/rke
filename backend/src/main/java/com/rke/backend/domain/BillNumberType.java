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

/** Master data: bill-number types, scoped per item category. */
@Entity
@Table(name = "bill_number_types")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BillNumberType extends TenantScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "item_category_id", nullable = false)
    private UUID itemCategoryId;

    @Column(name = "description")
    private String description;
}
