package com.rke.backend.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for correcting an existing return. The original bill number and
 * farmer are not editable — a return is intrinsically tied to the sale it
 * reverses, and re-pointing it elsewhere is not a "correction," it's a
 * different return. Only the return date, line items, and remarks can change.
 *
 * <p>Cumulative-return validation in {@code ReturnService.updateReturn} must
 * exclude this return's own current lines when recomputing "already returned"
 * quantity, otherwise a no-op re-save of the same quantities would appear to
 * double-count against the original sale.
 */
public record ReturnUpdateRequest(

        @NotNull LocalDate returnDate,

        @NotNull @NotEmpty @Valid List<ReturnLineItemRequest> items,

        String remarks
) {}
