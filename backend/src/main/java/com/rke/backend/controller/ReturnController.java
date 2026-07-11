package com.rke.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.dto.OriginalSaleResponse;
import com.rke.backend.dto.ReturnRequest;
import com.rke.backend.dto.TransactionResponse;
import com.rke.backend.service.ReturnService;

import jakarta.validation.Valid;

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
}
