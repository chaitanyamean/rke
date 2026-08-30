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

@Entity
@Table(name="notes_on_farmer")
@Filter(name= TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NoteOnFarmer extends TenantScopedEntity {
    
    @Column(name="content")
    private String content;

    @Column(name="farmer_id")
    private UUID farmerId;

    @Column(name="user_id")
    private UUID userId;

}
