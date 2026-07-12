package com.rke.backend.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.StaffUser;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.domain.enums.StaffRole;
import com.rke.backend.dto.StaffUserCreateRequest;
import com.rke.backend.dto.StaffUserResponse;
import com.rke.backend.dto.StaffUserUpdateRequest;
import com.rke.backend.exception.NotFoundException;
import com.rke.backend.repository.StaffUserRepository;
import com.rke.backend.security.CurrentUserService;

/**
 * Tenant-admin-facing staff user management. An ADMIN can create and edit
 * {@code STAFF}-role logins within their own tenant only — creating another
 * ADMIN or a SUPER_ADMIN is not possible through this service; that remains
 * exclusive to the super_admin tenant-onboarding flow in {@code TenantService}.
 *
 * <p>Every method here runs under the caller's tenant context, so the
 * Hibernate tenant filter (see {@code TenantFilterAspect}) already confines
 * reads/writes to the admin's own tenant — there is no separate tenant check
 * needed beyond what {@link CurrentUserService#getTenantId()} provides.
 */
@Service
public class StaffUserService {

    private final StaffUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public StaffUserService(StaffUserRepository repository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService,
                            CurrentUserService currentUserService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<StaffUserResponse> list() {
        return repository.findAllByOrderByUsernameAsc().stream()
                .map(StaffUserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffUserResponse get(UUID id) {
        return StaffUserResponse.from(require(id));
    }

    /**
     * Creates a new STAFF login in the acting admin's tenant. Password is
     * bcrypt-hashed via the shared {@link PasswordEncoder} bean — the same
     * pattern used by {@code TenantService} for the initial admin login — and
     * the plaintext is never persisted or logged.
     */
    @Transactional
    public StaffUserResponse create(StaffUserCreateRequest request) {
        UUID tenantId = currentUserService.getTenantId();
        String username = request.username().trim();
        requireUniqueUsername(tenantId, username, null);

        StaffUser user = StaffUser.builder()
                .tenantId(tenantId)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(StaffRole.STAFF)
                .active(true)
                .build();
        user = repository.save(user);

        auditService.record("staff_users", user.getId(), AuditAction.INSERT,
                null, auditSnapshot(user));
        return StaffUserResponse.from(user);
    }

    /**
     * Edits an existing STAFF login: full name, active status, and optionally a
     * password reset. Role is never touched here — it is not even read from the
     * request — so a STAFF row can never be promoted to ADMIN through this path.
     *
     * <p>The pre-edit snapshot captures {@code oldValues} for the audit trail
     * before any field is mutated, matching the pattern used by every other
     * {@code update} method in this codebase (e.g. {@code FarmerService.update}).
     */
    @Transactional
    public StaffUserResponse update(UUID id, StaffUserUpdateRequest request) {
        StaffUser user = require(id);
        if (user.getRole() != StaffRole.STAFF) {
            // Defense in depth: an admin should never be able to reach another
            // admin's or a super_admin's row through this tenant-scoped
            // endpoint, but the tenant filter alone doesn't rule out a same-
            // tenant admin row (there can be more than one admin per tenant).
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only staff-role users can be managed here");
        }

        Map<String, Object> before = auditSnapshot(user);

        user.setFullName(request.fullName().trim());
        user.setActive(request.active());

        String newPassword = request.newPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password must be at least 8 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        user = repository.save(user);
        auditService.record("staff_users", user.getId(), AuditAction.UPDATE,
                before, auditSnapshot(user));
        return StaffUserResponse.from(user);
    }

    private StaffUser require(UUID id) {
        StaffUser user = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Staff user", id));
        // Hibernate filter already scopes findById to the current tenant when a
        // tenant context is active, but double-check explicitly for clarity and
        // as a safeguard against the filter ever being bypassed.
        if (!Objects.equals(user.getTenantId(), currentUserService.getTenantId())) {
            throw NotFoundException.of("Staff user", id);
        }
        return user;
    }

    private void requireUniqueUsername(UUID tenantId, String username, UUID excludingId) {
        boolean exists = repository.findByUsername(username).stream()
                .filter(u -> !u.getId().equals(excludingId))
                .anyMatch(u -> tenantId.equals(u.getTenantId()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username already exists for this tenant: " + username);
        }
    }

    /** Audit snapshot excluding the password hash — never write credential material to audit_log. */
    private static Map<String, Object> auditSnapshot(StaffUser user) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("tenantId", user.getTenantId());
        snapshot.put("username", user.getUsername());
        snapshot.put("fullName", user.getFullName());
        snapshot.put("role", user.getRole());
        snapshot.put("active", user.isActive());
        return snapshot;
    }
}
