package com.rke.backend.dto.report;

import java.math.BigDecimal;

public record FarmerOutstandingRow(
        String farmerId,
        String farmerName,
        String fatherName,
        String villageName,
        BigDecimal outstandingBalance) {
}
