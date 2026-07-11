package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A line item of the original sale being looked up for a return, enriched with
 * how much of it has already been returned (across prior ACTIVE returns) and
 * how much is still eligible to be returned.
 */
public record OriginalSaleItemResponse(
        UUID itemId,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        /** Sum of quantities already returned against this line across all prior ACTIVE returns. */
        BigDecimal alreadyReturnedQuantity,
        /** {@code quantity - alreadyReturnedQuantity}, floored at zero. The real cap for a new return. */
        BigDecimal returnableQuantity
) {}
