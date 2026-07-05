package com.rke.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ReturnRequest(

        /** Used server-side to confirm the original bill belongs to this farmer. */
        @NotNull UUID farmerId,

        /** The bill_number of the CASH_SALE or CREDIT_SALE being reversed. */
        @NotBlank String originalBillNumber,

        @NotNull LocalDate returnDate,

        @NotNull @NotEmpty @Valid List<ReturnLineItemRequest> items,

        String remarks
) {}
