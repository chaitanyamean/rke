package com.rke.backend.simulation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * Tests for {@link BackendExceptionScenario}.
 *
 * <p>Does not require a Spring context or database.
 */
class BackendExceptionScenarioTest {

    private final BackendExceptionScenario scenario = new BackendExceptionScenario();

    @AfterEach
    void cleanup() {
        SimulationState.resetAll();
    }

    // -------------------------------------------------------------------------
    // Scenario disabled
    // -------------------------------------------------------------------------

    @Test
    void disabled_runDoesNothing() {
        // BACKEND_EXCEPTION is not enabled — run() must be a no-op.
        scenario.run("INC-003");
        // No exception, no side effects.
        assertThat(SimulationState.isActive(SimulationState.BACKEND_EXCEPTION)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Scenario enabled
    // -------------------------------------------------------------------------

    @Test
    void enabled_throwsSimulationException() {
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);

        assertThatThrownBy(() -> scenario.run("INC-003"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-003");
    }

    @Test
    void enabled_exceptionCarriesArithmeticCause() {
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);

        assertThatThrownBy(() -> scenario.run("INC-003"))
                .isInstanceOf(SimulationException.class)
                .cause()
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void enabled_exceptionMessageMentionsCorrelationId() {
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);

        assertThatThrownBy(() -> scenario.run("INC-003"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void enabled_incidentIdIsPreservedOnException() {
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);

        assertThatThrownBy(() -> scenario.run("INC-TEST"))
                .isInstanceOf(SimulationException.class)
                .satisfies(ex -> assertThat(((SimulationException) ex).getIncidentId())
                        .isEqualTo("INC-TEST"));
    }
}
