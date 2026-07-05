package com.rke.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.domain.BillNumberType;
import com.rke.backend.dto.BillNumberTypeRequest;
import com.rke.backend.service.BillNumberTypeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bill-number-types")
public class BillNumberTypeController {

    private final BillNumberTypeService service;

    public BillNumberTypeController(BillNumberTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<BillNumberType> list(@RequestParam(required = false) String search,
                                     @RequestParam(required = false) UUID categoryId) {
        return service.search(search, categoryId);
    }

    @GetMapping("/{id}")
    public BillNumberType get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public BillNumberType create(@Valid @RequestBody BillNumberTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public BillNumberType update(@PathVariable UUID id, @Valid @RequestBody BillNumberTypeRequest request) {
        return service.update(id, request);
    }
}
