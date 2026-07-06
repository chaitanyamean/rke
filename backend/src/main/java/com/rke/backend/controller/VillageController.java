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

import com.rke.backend.domain.Village;
import com.rke.backend.dto.VillageRequest;
import com.rke.backend.service.VillageService;

import jakarta.validation.Valid;

/**
 * Villages master data, global across all tenants. Any authenticated user
 * (staff, admin, or super_admin) can read, create, or update a village.
 */
@RestController
@RequestMapping("/api/villages")
public class VillageController {

    private final VillageService service;

    public VillageController(VillageService service) {
        this.service = service;
    }

    @GetMapping
    public List<Village> list(@RequestParam(required = false) String search) {
        return service.search(search);
    }

    @GetMapping("/{id}")
    public Village get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public Village create(@Valid @RequestBody VillageRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Village update(@PathVariable UUID id, @Valid @RequestBody VillageRequest request) {
        return service.update(id, request);
    }
}
