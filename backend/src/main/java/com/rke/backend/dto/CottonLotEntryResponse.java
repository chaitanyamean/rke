package com.rke.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.rke.backend.domain.CottonLotEntry;
import com.rke.backend.domain.Farmer;
import com.rke.backend.domain.Village;

public record CottonLotEntryResponse(
        UUID id,
        UUID farmerId,
        String farmerName,
        String fatherName,
        UUID villageId,
        String villageName,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount) {

    public static CottonLotEntryResponse from(CottonLotEntry e) {
        return new CottonLotEntryResponse(
                e.getId(), e.getFarmerId(), null, null,
                e.getVillageId(), null,
                e.getQuantity(), e.getPrice(), e.getAmount());
    }

    public static CottonLotEntryResponse from(CottonLotEntry e, Farmer farmer, Village village) {
        return new CottonLotEntryResponse(
                e.getId(),
                e.getFarmerId(),
                farmer != null ? farmer.getName() : null,
                farmer != null ? farmer.getFatherName() : null,
                e.getVillageId(),
                village != null ? village.getName() : null,
                e.getQuantity(), e.getPrice(), e.getAmount());
    }
}
