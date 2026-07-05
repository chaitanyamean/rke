package com.rke.backend.domain;

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

/** Master data for villages. All village references elsewhere are FKs to this table. */
@Entity
@Table(name = "villages")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Village extends TenantScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "landmark")
    private String landmark;
}
