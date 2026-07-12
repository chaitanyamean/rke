package com.rke.backend.controller;

import java.util.List;
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

import com.rke.backend.dto.StaffUserCreateRequest;
import com.rke.backend.dto.StaffUserResponse;
import com.rke.backend.dto.StaffUserUpdateRequest;
import com.rke.backend.service.StaffUserService;

import jakarta.validation.Valid;

/**
 * Tenant-admin staff user management — lets an ADMIN create and edit STAFF
 * logins within their own tenant. Restricted to ADMIN and SUPER_ADMIN (a
 * super_admin impersonating a tenant can also manage its staff); a STAFF user
 * gets a 403 on every endpoint here.
 */
@RestController
@RequestMapping("/api/staff-users")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class StaffUserController {

    private final StaffUserService service;

    public StaffUserController(StaffUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffUserResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public StaffUserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffUserResponse create(@Valid @RequestBody StaffUserCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public StaffUserResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody StaffUserUpdateRequest request) {
        return service.update(id, request);
    }
}
