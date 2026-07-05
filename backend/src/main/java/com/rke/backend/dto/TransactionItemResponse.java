package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.rke.backend.domain.TransactionItem;

public record TransactionItemResponse(
        UUID id,
        UUID itemId,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount
) {
    public static TransactionItemResponse from(TransactionItem item) {
        return new TransactionItemResponse(
                item.getId(),
                item.getItemId(),
                item.getQuantity(),
                item.getPrice(),
                item.getAmount()
        );
    }
}
