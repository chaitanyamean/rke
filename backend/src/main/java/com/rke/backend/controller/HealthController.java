package com.rke.backend.controller;

import java.sql.Connection;
import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness + readiness probe used by Render's health check config.
 * Probes the database connection so Render knows to route traffic only when the
 * app is genuinely ready (Flyway migrations have run, pool is up).
 *
 * <p>Returns 200 when healthy, 503 when the DB is unreachable.
 * Permitted without authentication in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("SELECT 1").executeQuery();
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "rke-backend",
                    "db", "reachable",
                    "timestamp", Instant.now().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DOWN",
                    "service", "rke-backend",
                    "db", "unreachable",
                    "error", e.getMessage(),
                    "timestamp", Instant.now().toString()));
        }
    }
}
