package com.rke.backend.simulation;

import java.time.Instant;

/**
 * Payload returned from every incident-trigger endpoint.
 *
 * <p>Intentionally a plain record — no JPA, no domain logic.
 */
public record IncidentResponse(
        String incidentId,
        String scenario,
        String status,
        String description,
        Instant triggeredAt
) {

    public static IncidentResponse triggered(String incidentId, String scenario, String description) {
        return new IncidentResponse(incidentId, scenario, "triggered", description, Instant.now());
    }

    public static IncidentResponse reset(String incidentId, String scenario) {
        return new IncidentResponse(incidentId, scenario, "reset", "Simulation reset — normal behaviour restored.", Instant.now());
    }
}
