package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ReturnLineItemRequest(

        @NotNull UUID itemId,

        /** Positive return quantity — must not exceed the originally sold quantity. */
        @NotNull @DecimalMin("0.001") BigDecimal quantity
) {}
