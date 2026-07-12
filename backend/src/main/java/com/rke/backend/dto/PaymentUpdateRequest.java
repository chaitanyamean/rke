package com.rke.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for correcting an existing payment/receipt. Bill number is not
 * editable (same reasoning as {@link SaleUpdateRequest}) — only the fields a
 * data-entry correction would plausibly touch are exposed.
 */
public record PaymentUpdateRequest(

        @NotNull UUID farmerId,

        @NotNull LocalDate transactionDate,

        @NotNull @DecimalMin("0.01") BigDecimal amount,

        String remarks
) {}
