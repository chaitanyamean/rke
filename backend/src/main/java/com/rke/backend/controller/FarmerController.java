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

import com.rke.backend.domain.Farmer;
import com.rke.backend.dto.FarmerRequest;
import com.rke.backend.service.FarmerService;

import jakarta.validation.Valid;

/**
 * Farmer registration and lookups. Open to any authenticated user (staff or
 * admin) — this is not admin-restricted master data.
 */
@RestController
@RequestMapping("/api/farmers")
public class FarmerController {

    private final FarmerService service;

    public FarmerController(FarmerService service) {
        this.service = service;
    }

    @GetMapping
    public List<Farmer> search(@RequestParam(required = false) String name,
                               @RequestParam(required = false) UUID villageId,
                               @RequestParam(required = false) String mobile) {
        return service.search(name, villageId, mobile);
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
}
