package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.rke.backend.domain.CottonLotEntry;

public record CottonLotEntryResponse(
        UUID id,
        UUID farmerId,
        UUID villageId,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount) {

    public static CottonLotEntryResponse from(CottonLotEntry e) {
        return new CottonLotEntryResponse(
                e.getId(), e.getFarmerId(), e.getVillageId(),
                e.getQuantity(), e.getPrice(), e.getAmount());
    }
}
