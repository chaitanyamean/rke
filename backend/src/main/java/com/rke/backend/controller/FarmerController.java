package com.rke.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import com.rke.backend.domain.Farmer;
import com.rke.backend.dto.FarmerRequest;
import com.rke.backend.service.FarmerService;
import com.rke.backend.service.PaymentService;

import jakarta.validation.Valid;

/**
 * Farmer registration and lookups. Open to any authenticated user (staff or
 * admin) — this is not admin-restricted master data.
 */
@RestController
@RequestMapping("/api/farmers")
public class FarmerController {

    private final FarmerService service;
    private final PaymentService paymentService;

    public FarmerController(FarmerService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<Farmer> search(@RequestParam(required = false) String name,
                               @RequestParam(required = false) String fatherName,
                               @RequestParam(required = false) UUID villageId,
                               @RequestParam(required = false) String mobile) {
        return service.search(name, fatherName, villageId, mobile);
    }

    @GetMapping("/{id}")
    public Farmer get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public Farmer create(@Valid @RequestBody FarmerRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Farmer update(@PathVariable UUID id, @Valid @RequestBody FarmerRequest request) {
        return service.update(id, request);
    }

    /**
     * Returns the outstanding balance for the farmer computed live from the
     * transaction log (credit_sales minus cash_payments and cash_receipts,
     * excluding voided transactions). Positive value = farmer owes money.
     */
    @GetMapping("/{id}/balance")
    public BigDecimal balance(@PathVariable UUID id) {
        return paymentService.getOutstandingBalance(id);
    }
}
