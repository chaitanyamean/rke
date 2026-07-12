package com.rke.backend.controller;

import java.util.UUID;

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

import com.rke.backend.dto.OriginalSaleResponse;
import com.rke.backend.dto.ReturnRequest;
import com.rke.backend.dto.ReturnUpdateRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.service.ReturnService;

import jakarta.validation.Valid;

/**
 * Return creation is open to STAFF and ADMIN; editing an existing return is
 * ADMIN-only (see {@code @PreAuthorize} on the PUT endpoint).
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    /**
     * Fetches an original CASH_SALE or CREDIT_SALE by bill number so the frontend
     * can display its line items before the user selects return quantities.
     */
    @GetMapping("/by-bill")
    public OriginalSaleResponse getOriginal(@RequestParam String billNumber) {
        return returnService.getOriginal(billNumber);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createReturn(@Valid @RequestBody ReturnRequest request) {
        return returnService.createReturn(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TransactionResponse getReturn(@PathVariable UUID id) {
        return returnService.getReturn(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TransactionResponse updateReturn(@PathVariable UUID id,
                                             @Valid @RequestBody ReturnUpdateRequest request) {
        return returnService.updateReturn(id, request);
    }
}
