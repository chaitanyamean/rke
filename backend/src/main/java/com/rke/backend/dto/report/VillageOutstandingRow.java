package com.rke.backend.dto.report;

import java.math.BigDecimal;

public record VillageOutstandingRow(
        String villageId,
        String villageName,
        BigDecimal outstandingBalance) {
}
