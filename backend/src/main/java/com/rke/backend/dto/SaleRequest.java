package com.rke.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SaleRequest(

        @NotNull UUID farmerId,

        /** The bill_number_types row that governs the sequence for this sale. */
        @NotNull UUID billNumberTypeId,

        /**
         * If supplied, used as-is (uniqueness check → 409 on conflict).
         * If null, the next sequence value from {@code next_bill_number()} is used.
         */
        String billNumber,

        @NotNull LocalDate transactionDate,

        @NotNull @NotEmpty @Valid List<SaleLineItemRequest> items,

        String remarks
) {}
