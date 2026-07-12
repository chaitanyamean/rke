package com.rke.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for correcting an existing cash/credit sale. Deliberately narrower
 * than {@link SaleRequest}: {@code billNumber} and {@code billNumberTypeId}
 * are not editable here — the bill number is tied to a generated sequence and
 * (for returns) referenced by {@code original_bill_number}, so renumbering an
 * existing sale is out of scope for a simple correction. Only the fields a
 * data-entry mistake would plausibly need fixing are exposed: farmer, date,
 * line items, and remarks.
 */
public record SaleUpdateRequest(

        @NotNull UUID farmerId,

        @NotNull LocalDate transactionDate,

        @NotNull @NotEmpty @Valid List<SaleLineItemRequest> items,

        String remarks
) {}
