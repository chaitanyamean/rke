package com.rke.backend.simulation.scenario;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * Tests for {@link CascadeFailureScenario}.
 */
class CascadeFailureScenarioTest {

    private final CascadeFailureScenario scenario = new CascadeFailureScenario();

    @AfterEach
    void cleanup() {
        SimulationState.resetAll();
    }

    @Test
    void disabled_runDoesNothing() {
        assertThatNoException().isThrownBy(() -> scenario.run("INC-005"));
    }

    @Test
    void enabled_throwsSimulationException() {
        SimulationState.enable(SimulationState.CASCADE_FAILURE);

        assertThatThrownBy(() -> scenario.run("INC-005"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-005");
    }

    @Test
    void enabled_exceptionMessageMentionsCascadeOrigin() {
        SimulationState.enable(SimulationState.CASCADE_FAILURE);

        assertThatThrownBy(() -> scenario.run("INC-005"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("PricingService");
    }

    @Test
    void enabled_exceptionHasCauseChain() {
        SimulationState.enable(SimulationState.CASCADE_FAILURE);

        assertThatThrownBy(() -> scenario.run("INC-005"))
                .isInstanceOf(SimulationException.class)
                // SimulationException → RuntimeException ([PricingService]) → IOException ([RatingEngine])
                .cause()
                .isInstanceOf(RuntimeException.class)
                .cause()
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("RatingEngine");
    }

    @Test
    void enabled_incidentIdPropagated() {
        SimulationState.enable(SimulationState.CASCADE_FAILURE);

        assertThatThrownBy(() -> scenario.run("INC-005"))
                .isInstanceOf(SimulationException.class)
                .satisfies(ex -> assertThat(((SimulationException) ex).getIncidentId())
                        .isEqualTo("INC-005"));
    }
}
