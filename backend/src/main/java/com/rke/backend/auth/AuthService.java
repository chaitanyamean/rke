package com.rke.backend.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.StaffUser;
import com.rke.backend.dto.LoginRequest;
import com.rke.backend.dto.UserResponse;
import com.rke.backend.repository.StaffUserRepository;
import com.rke.backend.repository.TenantRepository;
import com.rke.backend.security.StaffUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Session-based authentication against {@code staff_users}. On success the
 * security context is persisted to the HTTP session, so subsequent requests are
 * authenticated by the session cookie.
 */
@Service
public class AuthService {

    private final StaffUserRepository staffUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(StaffUserRepository staffUserRepository,
                       TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder,
                       SecurityContextRepository securityContextRepository) {
        this.staffUserRepository = staffUserRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        List<StaffUser> matches = staffUserRepository.findByUsername(request.username());

        // Optional tenant disambiguation (usernames are unique only per tenant).
        if (request.tenantSlug() != null && !request.tenantSlug().isBlank()) {
            UUID tenantId = tenantRepository.findBySlug(request.tenantSlug().trim())
                    .map(t -> t.getId())
                    .orElseThrow(AuthService::invalidCredentials);
            matches = matches.stream()
                    .filter(u -> tenantId.equals(u.getTenantId()))
                    .toList();
        }

        if (matches.isEmpty()) {
            throw invalidCredentials();
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username exists in multiple tenants; specify tenantSlug");
        }

        StaffUser user = matches.get(0);
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        StaffUserPrincipal principal = StaffUserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return UserResponse.from(user);
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
