package com.rke.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.enums.TransactionType;
import com.rke.backend.dto.SaleRequest;
import com.rke.backend.dto.TransactionItemResponse;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.repository.TransactionItemRepository;
import com.rke.backend.repository.TransactionRepository;
import com.rke.backend.service.SalesService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SalesService salesService;
    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;

    public SaleController(SalesService salesService,
                          TransactionRepository transactionRepository,
                          TransactionItemRepository transactionItemRepository) {
        this.salesService = salesService;
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
    }

    @PostMapping("/cash")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createCashSale(@Valid @RequestBody SaleRequest request) {
        return salesService.createSale(request, TransactionType.CASH_SALE);
    }

    @PostMapping("/credit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createCreditSale(@Valid @RequestBody SaleRequest request) {
        return salesService.createSale(request, TransactionType.CREDIT_SALE);
    }

    @GetMapping("/farmer/{farmerId}")
    public List<TransactionResponse> byFarmer(@PathVariable UUID farmerId) {
        List<Transaction> txs = transactionRepository.findByFarmerIdOrderByTransactionDateDesc(farmerId);
        return txs.stream().map(tx -> {
            List<TransactionItemResponse> items = transactionItemRepository
                    .findByTransactionId(tx.getId()).stream()
                    .map(TransactionItemResponse::from)
                    .toList();
            return TransactionResponse.from(tx, items);
        }).toList();
    }
}
