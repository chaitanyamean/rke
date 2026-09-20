package com.rke.backend.simulation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Additional Spring Security configuration active <strong>only</strong> on the
 * {@code dev} profile.
 *
 * <p>Registers a higher-priority security filter chain (order 1, before the
 * default order-100 chain in {@link com.rke.backend.security.SecurityConfig})
 * that permits all requests to {@code /api/test/incidents/**} without
 * authentication.
 *
 * <p>Because this bean only exists when {@code spring.profiles.active=dev}, the
 * test endpoints are inaccessible in production — the path is not just
 * unauthorised, it genuinely does not exist.
 */
@Configuration
@Profile("dev")
public class SimulationSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain simulationSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/test/incidents/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(reg -> reg
                .requestMatchers(HttpMethod.OPTIONS, "/api/test/incidents/**").permitAll()
                .requestMatchers("/api/test/incidents/**").permitAll());
        return http.build();
    }
}
