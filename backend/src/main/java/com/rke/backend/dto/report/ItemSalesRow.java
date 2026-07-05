package com.rke.backend.dto.report;

import java.math.BigDecimal;

public record ItemSalesRow(
        String itemId,
        String itemName,
        String categoryId,
        String categoryName,
        BigDecimal totalQuantity,
        BigDecimal totalAmount) {
}
