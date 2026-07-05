package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CottonLotEntryRequest(
        @NotNull UUID farmerId,
        @NotNull UUID villageId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal price) {
}
