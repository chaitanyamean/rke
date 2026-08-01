package com.rke.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.dto.CottonLotRequest;
import com.rke.backend.dto.CottonLotResponse;
import com.rke.backend.security.RequiresFeature;
import com.rke.backend.service.CottonService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cotton-lots")
public class CottonController {

    private final CottonService cottonService;

    public CottonController(CottonService cottonService) {
        this.cottonService = cottonService;
    }

    /** Peek at the next serial number without incrementing the sequence. */
    @GetMapping("/serial-preview")
    @RequiresFeature("cotton_procurement")
    public String serialPreview() {
        return cottonService.peekSerialNumber();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresFeature("cotton_procurement")
    public CottonLotResponse create(@Valid @RequestBody CottonLotRequest request) {
        return cottonService.createCottonLot(request);
    }

    @GetMapping
    @RequiresFeature("cotton_procurement")
    public List<CottonLotResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return cottonService.listLots(fromDate, toDate);
    }

    @GetMapping("/{id}")
    @RequiresFeature("cotton_procurement")
    public CottonLotResponse get(@PathVariable UUID id) {
        return cottonService.getLot(id);
    }

    @PutMapping("/{id}")
    @RequiresFeature("cotton_procurement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CottonLotResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody CottonLotRequest request) {
        return cottonService.updateCottonLot(id, request);
    }
}
