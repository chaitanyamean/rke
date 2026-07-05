package com.rke.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.rke.backend.domain.Tenant;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.TenantRequest;
import com.rke.backend.dto.TenantResponse;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.TenantRepository;
import com.rke.backend.security.CurrentUserService;
import com.rke.backend.security.TenantContextFilter;

import jakarta.servlet.http.HttpSession;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public TenantService(TenantRepository tenantRepository,
                         StorageService storageService,
                         AuditService auditService,
                         CurrentUserService currentUserService) {
        this.tenantRepository = tenantRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listAll() {
        return tenantRepository.findAll().stream()
                .map(TenantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getById(UUID id) {
        return TenantResponse.from(require(id));
    }

    /**
     * Returns the branding for the current request's tenant (from TenantContext).
     * Returns {@code null} for a super_admin without impersonation.
     */
    @Transactional(readOnly = true)
    public TenantResponse currentTenant() {
        UUID tenantId = currentUserService.getTenantId();
        if (tenantId == null) {
            return null;
        }
        return TenantResponse.from(require(tenantId));
    }

    @Transactional
    public TenantResponse create(TenantRequest request) {
        Tenant tenant = Tenant.builder()
                .name(request.name().trim())
                .slug(request.slug().trim())
                .primaryColor(blankToNull(request.primaryColor()))
                .active(request.active())
                .build();
        tenant = tenantRepository.save(tenant);
        // Audit logged via standard application log (no tenant context for creation).
        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse update(UUID id, TenantRequest request) {
        Tenant tenant = require(id);
        var before = auditService.snapshot(tenant);
        tenant.setName(request.name().trim());
        tenant.setSlug(request.slug().trim());
        tenant.setPrimaryColor(blankToNull(request.primaryColor()));
        tenant.setActive(request.active());
        tenant = tenantRepository.save(tenant);
        auditService.recordForTenant(id, "tenants", id, AuditAction.UPDATE,
                before, auditService.snapshot(tenant));
        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse uploadLogo(UUID id, MultipartFile file) {
        Tenant tenant = require(id);
        try {
            String url = storageService.uploadLogo(
                    file.getOriginalFilename(), file.getBytes(), file.getContentType());
            tenant.setLogoUrl(url);
            tenant = tenantRepository.save(tenant);
            auditService.recordForTenant(id, "tenants", id, AuditAction.UPDATE,
                    null, java.util.Map.of("logoUrl", url));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
        return TenantResponse.from(tenant);
    }

    /**
     * Sets the impersonated tenant in the HTTP session so subsequent requests
     * from this super_admin are scoped to that tenant.
     */
    @Transactional
    public TenantResponse startImpersonation(UUID tenantId, HttpSession session) {
        Tenant tenant = require(tenantId);
        session.setAttribute(TenantContextFilter.IMPERSONATED_TENANT_ATTR, tenantId);
        auditService.recordForTenant(tenantId, "tenant_impersonation", tenantId,
                AuditAction.UPDATE, null,
                java.util.Map.of(
                        "superAdminId", currentUserService.getCurrentUserId().toString(),
                        "action", "start_impersonation"));
        return TenantResponse.from(tenant);
    }

    /** Clears the impersonation session attribute. */
    @Transactional
    public void exitImpersonation(HttpSession session) {
        UUID impersonatedId = (UUID) session.getAttribute(TenantContextFilter.IMPERSONATED_TENANT_ATTR);
        if (impersonatedId != null) {
            auditService.recordForTenant(impersonatedId, "tenant_impersonation", impersonatedId,
                    AuditAction.UPDATE, null,
                    java.util.Map.of(
                            "superAdminId", currentUserService.getCurrentUserId().toString(),
                            "action", "exit_impersonation"));
            session.removeAttribute(TenantContextFilter.IMPERSONATED_TENANT_ATTR);
        }
    }

    private Tenant require(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Tenant", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
