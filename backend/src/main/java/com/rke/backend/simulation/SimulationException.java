package com.rke.backend.simulation;

/**
 * Base class for all controlled simulation exceptions.
 *
 * <p>Extending {@link RuntimeException} so that these propagate through
 * the normal Spring exception-handling chain and reach
 * {@link com.rke.backend.exception.GlobalExceptionHandler} unchanged,
 * producing realistic HTTP 5xx responses and stack traces in the logs.
 */
public class SimulationException extends RuntimeException {

    private final String incidentId;

    public SimulationException(String incidentId, String message) {
        super("[" + incidentId + "] " + message);
        this.incidentId = incidentId;
    }

    public SimulationException(String incidentId, String message, Throwable cause) {
        super("[" + incidentId + "] " + message, cause);
        this.incidentId = incidentId;
    }

    public String getIncidentId() {
        return incidentId;
    }
}
