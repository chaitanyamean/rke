package com.rke.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.rke.backend.simulation.SimulationException;

import java.util.Map;

/**
 * Tests for the {@link SimulationException} handler in
 * {@link GlobalExceptionHandler}.
 *
 * <p>Instantiates the handler directly — no Spring context required.
 */
class GlobalExceptionHandlerSimulationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void simulationException_returns500() {
        var ex = new SimulationException("INC-001", "pool exhausted");
        ResponseEntity<Map<String, Object>> response = handler.handleSimulation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void simulationException_bodyContainsIncidentId() {
        var ex = new SimulationException("INC-003", "overflow");
        var body = handler.handleSimulation(ex).getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("incidentId")).isEqualTo("INC-003");
    }

    @Test
    void simulationException_bodyContainsSimulatedFailureFlag() {
        var ex = new SimulationException("INC-002", "slow query");
        var body = handler.handleSimulation(ex).getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("simulatedFailure")).isEqualTo(true);
    }

    @Test
    void simulationException_bodyContainsTimestampAndStatus() {
        var ex = new SimulationException("INC-005", "cascade");
        var body = handler.handleSimulation(ex).getBody();

        assertThat(body).isNotNull();
        assertThat(body).containsKey("timestamp");
        assertThat(body.get("status")).isEqualTo(500);
        assertThat(body.get("error")).isEqualTo("Internal Server Error");
    }

    @Test
    void simulationException_messageContainsIncidentIdPrefix() {
        var ex = new SimulationException("INC-004", "config regression");
        var body = handler.handleSimulation(ex).getBody();

        assertThat(body).isNotNull();
        assertThat((String) body.get("message")).contains("INC-004");
    }
}
