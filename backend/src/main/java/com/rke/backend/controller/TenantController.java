package com.rke.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rke.backend.dto.TenantCreateRequest;
import com.rke.backend.dto.TenantCreateResponse;
import com.rke.backend.dto.TenantRequest;
import com.rke.backend.dto.TenantResponse;
import com.rke.backend.service.TenantService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Super-admin-only tenant management. All endpoints here require
 * {@code ROLE_SUPER_ADMIN}; the {@link com.rke.backend.security.TenantScopeInterceptor}
 * enforces this at the HTTP layer as well.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public List<TenantResponse> list() {
        return tenantService.listAll();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return tenantService.getById(id);
    }

    /**
     * Creates a new tenant together with its first admin login, in one atomic
     * step. This is the only way (besides Flyway seed data) a tenant becomes
     * usable — without an admin login there would be no way to sign into it.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantCreateResponse create(@Valid @RequestBody TenantCreateRequest request) {
        return tenantService.create(request);
    }

    @PutMapping("/{id}")
    public TenantResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody TenantRequest request) {
        return tenantService.update(id, request);
    }

    /**
     * Uploads a logo image to S3-compatible storage and saves the URL to
     * {@code tenants.logo_url}. Accepts {@code multipart/form-data} with a
     * single file part named {@code file}.
     */
    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenantResponse uploadLogo(@PathVariable UUID id,
                                      @RequestParam("file") MultipartFile file) {
        return tenantService.uploadLogo(id, file);
    }

    /**
     * Starts super_admin impersonation of a tenant. Subsequent requests from this
     * session are scoped to that tenant's data until {@code /exit} is called.
     * The action is written to the target tenant's audit_log.
     */
    @PostMapping("/{id}/impersonate")
    public TenantResponse startImpersonation(@PathVariable UUID id, HttpSession session) {
        return tenantService.startImpersonation(id, session);
    }

    /**
     * Exits impersonation, returning the super_admin to cross-tenant scope.
     */
    @DeleteMapping("/impersonate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exitImpersonation(HttpSession session) {
        tenantService.exitImpersonation(session);
    }
}
