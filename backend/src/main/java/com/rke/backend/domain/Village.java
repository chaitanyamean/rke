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
 * Global master data for villages, shared across all tenants. All village
 * references elsewhere are FKs to this table.
 */
@Entity
@Table(name = "villages")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Village extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "landmark")
    private String landmark;
}
