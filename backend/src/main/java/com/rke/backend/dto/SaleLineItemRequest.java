package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SaleLineItemRequest(

        @NotNull UUID itemId,

        @NotNull @DecimalMin("0.001") BigDecimal quantity,

        /** Optional override. If null, the service resolves price from the item master. */
        BigDecimal price
) {}
