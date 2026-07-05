package com.rke.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(

        @NotNull UUID farmerId,

        @NotNull UUID billNumberTypeId,

        /** Mandatory for payments — always user-entered, not auto-generated. */
        @NotBlank String billNumber,

        @NotNull LocalDate transactionDate,

        @NotNull @DecimalMin("0.01") BigDecimal amount,

        String remarks
) {}
