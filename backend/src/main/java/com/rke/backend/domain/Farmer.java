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
 * A farmer. Natural identity (placeholder, pending client confirmation) is
 * name + father_name + village within a tenant — see the note in V1.
 */
@Entity
@Table(name = "farmers")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Farmer extends TenantScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "father_name")
    private String fatherName;

    @Column(name = "village_id", nullable = false)
    private UUID villageId;

    @Column(name = "address")
    private String address;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "reference")
    private String reference;
}
