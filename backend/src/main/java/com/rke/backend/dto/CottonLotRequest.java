package com.rke.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CottonLotRequest(
        String vehicleRegistrationNumber,
        String mutaHamaliName,
        @NotNull @DecimalMin("0") BigDecimal commonPrice,
        @NotNull LocalDate lotDate,
        @NotNull @NotEmpty @Valid List<CottonLotEntryRequest> entries) {
}
