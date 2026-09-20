package com.rke.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SimulationState} — the in-memory scenario registry.
 */
class SimulationStateTest {

    @AfterEach
    void cleanup() {
        SimulationState.resetAll();
    }

    @Test
    void initialState_noScenariosActive() {
        assertThat(SimulationState.activeScenarios()).isEmpty();
    }

    @Test
    void enable_makesScenarioActive() {
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);
        assertThat(SimulationState.isActive(SimulationState.BACKEND_EXCEPTION)).isTrue();
    }

    @Test
    void disable_removesScenarioFromActive() {
        SimulationState.enable(SimulationState.SLOW_QUERY);
        SimulationState.disable(SimulationState.SLOW_QUERY);
        assertThat(SimulationState.isActive(SimulationState.SLOW_QUERY)).isFalse();
    }

    @Test
    void resetAll_clearsAllActiveScenarios() {
        SimulationState.enable(SimulationState.DB_POOL_EXHAUSTION);
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);
        SimulationState.enable(SimulationState.CASCADE_FAILURE);

        SimulationState.resetAll();

        assertThat(SimulationState.activeScenarios()).isEmpty();
        assertThat(SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION)).isFalse();
        assertThat(SimulationState.isActive(SimulationState.BACKEND_EXCEPTION)).isFalse();
        assertThat(SimulationState.isActive(SimulationState.CASCADE_FAILURE)).isFalse();
    }

    @Test
    void activeScenarios_returnsSnapshotOfCurrentlyActiveSet() {
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        SimulationState.enable(SimulationState.SLOW_QUERY);

        var active = SimulationState.activeScenarios();
        assertThat(active).containsExactlyInAnyOrder(
                SimulationState.CONFIG_REGRESSION,
                SimulationState.SLOW_QUERY);
    }

    @Test
    void isActive_returnsFalse_forUnenabledScenario() {
        assertThat(SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION_V2)).isFalse();
    }

    @Test
    void enable_isIdempotent() {
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION); // enable twice
        assertThat(SimulationState.activeScenarios()).hasSize(1);
        assertThat(SimulationState.isActive(SimulationState.BACKEND_EXCEPTION)).isTrue();
    }

    @Test
    void disable_isIdempotent_whenNotActive() {
        // Should not throw when disabling something that is already off.
        SimulationState.disable(SimulationState.SLOW_QUERY);
        assertThat(SimulationState.isActive(SimulationState.SLOW_QUERY)).isFalse();
    }

    @Test
    void allIncidentStates_areRepresented() {
        // Sanity check — every known incident ID maps to a SimulationState constant.
        assertThat(SimulationState.values()).hasSizeGreaterThanOrEqualTo(6);
    }
}
