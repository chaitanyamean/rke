package com.rke.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Lightweight unit test for the health endpoint. It intentionally avoids
 * loading the full Spring context so that {@code make test} does not require
 * a running database.
 */
class HealthControllerTest {

    @Test
    void healthReportsUp() {
        Map<String, Object> body = new HealthController().health();

        assertThat(body).containsEntry("status", "UP");
        assertThat(body).containsEntry("service", "rke-backend");
        assertThat(body).containsKey("timestamp");
    }
}
