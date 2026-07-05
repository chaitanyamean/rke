package com.rke.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.dto.TenantResponse;
import com.rke.backend.service.TenantService;

/**
 * Lightweight endpoint any authenticated user can call to get their current
 * tenant's branding (logo, primary color). The frontend reads this on startup
 * to apply runtime theming. Returns {@code null} for a super_admin without an
 * active impersonation session.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantBrandingController {

    private final TenantService tenantService;

    public TenantBrandingController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/current")
    public TenantResponse current() {
        return tenantService.currentTenant();
    }
}
