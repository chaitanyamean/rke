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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.dto.PaymentRequest;
import com.rke.backend.dto.PaymentUpdateRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.service.PaymentService;

import jakarta.validation.Valid;

/**
 * Payment/receipt creation is open to STAFF and ADMIN; editing an existing one
 * is ADMIN-only (see {@code @PreAuthorize} on the PUT endpoints).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createPayment(@Valid @RequestBody PaymentRequest request) {
        return paymentService.createPayment(request, TransactionType.CASH_PAYMENT);
    }

    @PostMapping("/receipt")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createReceipt(@Valid @RequestBody PaymentRequest request) {
        return paymentService.createPayment(request, TransactionType.CASH_RECEIPT);
    }

    @GetMapping("/payment/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TransactionResponse getPayment(@PathVariable UUID id) {
        return paymentService.getPayment(id, TransactionType.CASH_PAYMENT);
    }

    @GetMapping("/receipt/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TransactionResponse getReceipt(@PathVariable UUID id) {
        return paymentService.getPayment(id, TransactionType.CASH_RECEIPT);
    }

    @PutMapping("/payment/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TransactionResponse updatePayment(@PathVariable UUID id,
                                              @Valid @RequestBody PaymentUpdateRequest request) {
        return paymentService.updatePayment(id, request, TransactionType.CASH_PAYMENT);
    }

    @PutMapping("/receipt/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TransactionResponse updateReceipt(@PathVariable UUID id,
                                              @Valid @RequestBody PaymentUpdateRequest request) {
        return paymentService.updatePayment(id, request, TransactionType.CASH_RECEIPT);
    }
}
