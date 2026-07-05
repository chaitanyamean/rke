package com.rke.backend.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.rke.backend.domain.StaffUser;
import com.rke.backend.domain.enums.StaffRole;

import lombok.Getter;

/**
 * Authenticated principal stored in the HTTP session. Carries the identifiers
 * needed downstream: {@code userId} (for {@code audit_log.changed_by}) and
 * {@code tenantId} (to scope tenant queries).
 */
@Getter
public class StaffUserPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID tenantId;
    private final String username;
    private final String fullName;
    private final transient String passwordHash;
    private final StaffRole role;
    private final boolean active;

    public StaffUserPrincipal(UUID userId, UUID tenantId, String username, String fullName,
                              String passwordHash, StaffRole role, boolean active) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public static StaffUserPrincipal from(StaffUser user) {
        return new StaffUserPrincipal(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getFullName(),
                user.getPasswordHash(),
                user.getRole(),
                user.isActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring's hasRole('ADMIN') checks for the authority "ROLE_ADMIN".
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
