package com.rke.backend.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Micrometer Tracing to the {@link OpenTelemetry} instance installed by the
 * OpenTelemetry Java agent.
 *
 * <p>When the agent is attached (docker-compose / the production image) it registers
 * itself as the {@code GlobalOpenTelemetry} instance. Exposing that same instance as a
 * Spring bean means:
 * <ul>
 *   <li>manual spans created through Micrometer's {@code Tracer} share trace context
 *       with the agent's auto-instrumented spans (HTTP, JDBC/Hibernate, Postgres), and</li>
 *   <li>those manual spans are exported by the agent's pipeline &mdash; we do not run a
 *       second, competing OpenTelemetry SDK inside the app, so there are no duplicate
 *       spans.</li>
 * </ul>
 *
 * <p>This bean overrides Spring Boot's auto-configured SDK (which is
 * {@code @ConditionalOnMissingBean(OpenTelemetry.class)}). When the agent is <em>not</em>
 * attached (e.g. {@code make dev}), {@code GlobalOpenTelemetry.get()} returns a no-op
 * instance, so manual spans become cheap no-ops rather than failing.
 */
@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
