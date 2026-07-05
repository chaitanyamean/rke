package com.rke.backend.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.service.SalesService;

@RestController
@RequestMapping("/api/bill-numbers")
public class BillNumberController {

    private final SalesService salesService;

    public BillNumberController(SalesService salesService) {
        this.salesService = salesService;
    }

    /**
     * Returns the next bill number for the given category without incrementing
     * the sequence. Used by the frontend to pre-fill the bill number field.
     */
    @GetMapping("/preview")
    public String preview(@RequestParam UUID categoryId) {
        return salesService.previewBillNumber(categoryId);
    }
}
