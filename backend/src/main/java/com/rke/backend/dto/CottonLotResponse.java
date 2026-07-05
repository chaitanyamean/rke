package com.rke.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.rke.backend.domain.CottonLot;

public record CottonLotResponse(
        UUID id,
        String vehicleSerialNumber,
        String vehicleRegistrationNumber,
        String mutaHamaliName,
        BigDecimal commonPrice,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        LocalDate lotDate,
        List<CottonLotEntryResponse> entries) {

    public static CottonLotResponse from(CottonLot lot, List<CottonLotEntryResponse> entries) {
        return new CottonLotResponse(
                lot.getId(),
                lot.getVehicleSerialNumber(),
                lot.getVehicleRegistrationNumber(),
                lot.getMutaHamaliName(),
                lot.getCommonPrice(),
                lot.getTotalQuantity(),
                lot.getTotalAmount(),
                lot.getLotDate(),
                entries);
    }
}
