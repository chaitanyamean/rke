package com.rke.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * A cotton procurement lot. {@code vehicleSerialNumber} is auto-generated and
 * unique per tenant (generation handled in the service layer). Soft-delete +
 * void via {@link FinancialEntity}.
 */
@Entity
@Table(name = "cotton_lots")
@Filter(name = TenantFilters.NAME)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CottonLot extends FinancialEntity {

    @Column(name = "vehicle_serial_number", nullable = false)
    private String vehicleSerialNumber;

    @Column(name = "vehicle_registration_number")
    private String vehicleRegistrationNumber;

    @Column(name = "muta_hamali_name")
    private String mutaHamaliName;

    @Column(name = "common_price", nullable = false)
    private BigDecimal commonPrice;

    @Column(name = "total_quantity", nullable = false)
    private BigDecimal totalQuantity;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "lot_date", nullable = false)
    private LocalDate lotDate;
}
