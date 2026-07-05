package com.rke.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.dto.PaymentRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.service.PaymentService;

import jakarta.validation.Valid;

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
}
