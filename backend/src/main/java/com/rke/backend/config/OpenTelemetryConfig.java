package com.rke.backend.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.extension.trace.propagation.B3Propagator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds and exposes the OpenTelemetry SDK as a Spring bean.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>When {@code management.otlp.tracing.endpoint} (i.e. the
 *       {@code OTEL_EXPORTER_OTLP_ENDPOINT} env var) is set to a non-empty value,
 *       the SDK is wired with an OTLP/gRPC {@link BatchSpanProcessor} that exports
 *       spans to the configured collector.</li>
 *   <li>When the endpoint is blank or absent the SDK is configured with a
 *       <em>no-export</em> processor — spans are still created and correlated in
 *       the application (trace/span IDs appear in logs) but nothing is sent over
 *       the wire.  This means the app starts and runs correctly without a
 *       collector running.</li>
 * </ul>
 *
 * <h3>Resource attributes</h3>
 * <ul>
 *   <li>{@code service.name} — from {@code OTEL_SERVICE_NAME} (default: {@code rke-backend})</li>
 *   <li>{@code deployment.environment} — from {@code DEPLOYMENT_ENVIRONMENT} (default: {@code development})</li>
 * </ul>
 *
 * <h3>Propagation</h3>
 * W3C TraceContext + W3C Baggage (primary) and B3 single-header (secondary) so
 * the app interoperates with any modern OTEL-aware service or collector.
 *
 * <h3>Interaction with the OpenTelemetry Java agent</h3>
 * This bean is annotated {@code @ConditionalOnMissingBean} — if you attach
 * the OTEL Java agent at JVM startup it registers itself as
 * {@code GlobalOpenTelemetry}, Spring Boot picks it up automatically, and this
 * manual SDK is skipped.  The agent path remains available for environments
 * where it is preferred.
 */
@Configuration
public class OpenTelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryConfig.class);

    @Value("${management.otlp.tracing.endpoint:#{null}}")
    private String otlpEndpoint;

    @Value("${otel.service.name:rke-backend}")
    private String serviceName;

    @Value("${otel.deployment.environment:development}")
    private String deploymentEnvironment;

    @Value("${management.tracing.sampling.probability:1.0}")
    private double samplingProbability;

    /**
     * Builds the OpenTelemetry SDK.
     *
     * <p>Spring Boot's {@code @ConditionalOnMissingBean(OpenTelemetry.class)} on
     * its own auto-configuration means this bean takes precedence over the
     * default no-op bean while still yielding to the Java agent when present.
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        // "service.name" is the stable OTel semantic convention key.
        // Using AttributeKey.stringKey directly avoids the opentelemetry-semconv
        // alpha jar which is not reliably available in offline Maven builds.
        Resource resource = Resource.getDefault().toBuilder()
                .putAll(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName,
                        AttributeKey.stringKey("deployment.environment"), deploymentEnvironment))
                .build();

        Sampler sampler = (samplingProbability >= 1.0)
                ? Sampler.alwaysOn()
                : (samplingProbability <= 0.0)
                        ? Sampler.alwaysOff()
                        : Sampler.traceIdRatioBased(samplingProbability);

        SdkTracerProvider tracerProvider;

        if (StringUtils.hasText(otlpEndpoint)) {
            log.info("OpenTelemetry: OTLP/gRPC exporter configured → {}", otlpEndpoint);

            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .build();

            tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .setSampler(sampler)
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                    .build();
        } else {
            log.info("OpenTelemetry: no OTLP endpoint configured — spans created locally (no export). " +
                    "Set OTEL_EXPORTER_OTLP_ENDPOINT to enable export.");

            tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .setSampler(sampler)
                    // No exporter: spans are recorded in-process for log correlation
                    // but never sent over the network.
                    .build();
        }

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                        TextMapPropagator.composite(
                                W3CTraceContextPropagator.getInstance(),
                                io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator.getInstance(),
                                B3Propagator.injectingSingleHeader())))
                .buildAndRegisterGlobal();

        log.info("OpenTelemetry SDK initialised: service.name={} environment={} sampling={}",
                serviceName, deploymentEnvironment, samplingProbability);

        return sdk;
    }
}
