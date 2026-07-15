package com.rke.backend.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.CottonLotSequence;
import com.rke.backend.domain.StaffUser;
import com.rke.backend.domain.Tenant;
import com.rke.backend.domain.TransactionNoSequence;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.domain.enums.StaffRole;
import com.rke.backend.dto.TenantCreateRequest;
import com.rke.backend.dto.TenantCreateResponse;
import com.rke.backend.dto.TenantRequest;
import com.rke.backend.dto.TenantResponse;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.CottonLotSequenceRepository;
import com.rke.backend.repository.StaffUserRepository;
import com.rke.backend.repository.TenantRepository;
import com.rke.backend.repository.TransactionNoSequenceRepository;
import com.rke.backend.security.CurrentUserService;
import com.rke.backend.security.TenantContextFilter;

import jakarta.servlet.http.HttpSession;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final StaffUserRepository staffUserRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final CottonLotSequenceRepository cottonLotSequenceRepository;
    private final TransactionNoSequenceRepository transactionNoSequenceRepository;

    public TenantService(TenantRepository tenantRepository,
                         StaffUserRepository staffUserRepository,
                         StorageService storageService,
                         AuditService auditService,
                         CurrentUserService currentUserService,
                         PasswordEncoder passwordEncoder,
                         CottonLotSequenceRepository cottonLotSequenceRepository,
                         TransactionNoSequenceRepository transactionNoSequenceRepository) {
        this.tenantRepository = tenantRepository;
        this.staffUserRepository = staffUserRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.cottonLotSequenceRepository = cottonLotSequenceRepository;
        this.transactionNoSequenceRepository = transactionNoSequenceRepository;
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

    /**
     * Creates a tenant and its first admin login atomically. Without this, a
     * newly created tenant would have no way to sign in — only RK Enterprises'
     * seeded {@code staff_users} row (via Flyway) exists otherwise.
     *
     * <p>The admin username only needs to be unique within the brand-new tenant,
     * which is trivially true since it has no other users yet — but the check is
     * still performed via {@link #requireUniqueUsername} to share the same
     * validation path used everywhere else usernames are assigned, and to guard
     * against the (currently impossible, but future-proofed) case of retrying
     * this call against an existing tenant id.
     *
     * <p>The plaintext password is never persisted or logged: only its bcrypt
     * hash is stored on the {@code StaffUser} row, and the audit snapshot for
     * the new user explicitly omits the password fields (see
     * {@link #adminAuditSnapshot}).
     */
    @Transactional
    public TenantCreateResponse create(TenantCreateRequest request) {
        Tenant tenant = Tenant.builder()
                .name(request.name().trim())
                .slug(request.slug().trim())
                .primaryColor(blankToNull(request.primaryColor()))
                .active(request.active())
                .build();
        tenant = tenantRepository.save(tenant);

        String username = request.adminUsername().trim();
        requireUniqueUsername(tenant.getId(), username);

        StaffUser admin = StaffUser.builder()
                .tenantId(tenant.getId())
                .username(username)
                .passwordHash(passwordEncoder.encode(request.adminPassword()))
                .fullName(request.adminFullName().trim())
                .role(StaffRole.ADMIN)
                .active(true)
                .build();
        admin = staffUserRepository.save(admin);

        auditService.recordForTenant(tenant.getId(), "tenants", tenant.getId(),
                AuditAction.INSERT, null, auditService.snapshot(tenant));
        auditService.recordForTenant(tenant.getId(), "staff_users", admin.getId(),
                AuditAction.INSERT, null, adminAuditSnapshot(admin));

        // Provision the per-tenant sequence rows so the new tenant can immediately
        // start creating transactions, cotton lots, etc. without manual DB seeding.
        // bill_number_sequences are per-category and created when categories are added.
        cottonLotSequenceRepository.save(CottonLotSequence.builder()
                .tenantId(tenant.getId())
                .currentSequence(0)
                .prefix("CTNL")
                .paddingWidth(4)
                .formatTemplate("{PREFIX}-{SEQ}")
                .build());

        transactionNoSequenceRepository.save(TransactionNoSequence.builder()
                .tenantId(tenant.getId())
                .currentSequence(0)
                .build());

        return TenantCreateResponse.of(TenantResponse.from(tenant), admin.getUsername());
    }

    /**
     * Same uniqueness rule enforced at login time (username unique per tenant),
     * checked proactively here so a duplicate produces a clean 409 instead of
     * relying solely on the DB's unique index to reject the insert.
     */
    private void requireUniqueUsername(UUID tenantId, String username) {
        boolean exists = staffUserRepository.findByUsername(username).stream()
                .anyMatch(u -> tenantId.equals(u.getTenantId()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username already exists for this tenant: " + username);
        }
    }

    /**
     * Audit snapshot for a newly created StaffUser that deliberately excludes
     * the password hash — audit_log must never carry credential material, even
     * hashed, since it's readable by anyone with audit access.
     */
    private static Map<String, Object> adminAuditSnapshot(StaffUser admin) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("id", admin.getId());
        snapshot.put("tenantId", admin.getTenantId());
        snapshot.put("username", admin.getUsername());
        snapshot.put("fullName", admin.getFullName());
        snapshot.put("role", admin.getRole());
        snapshot.put("active", admin.isActive());
        return snapshot;
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
