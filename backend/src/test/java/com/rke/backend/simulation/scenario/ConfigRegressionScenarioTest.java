package com.rke.backend.simulation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * Tests for {@link ConfigRegressionScenario}.
 *
 * <ul>
 *   <li>Verifies the known-good path (no exception).</li>
 *   <li>Verifies the regression path (exception with meaningful message).</li>
 *   <li>Verifies enable/reset lifecycle.</li>
 * </ul>
 */
class ConfigRegressionScenarioTest {

    @AfterEach
    void cleanup() {
        SimulationState.resetAll();
    }

    // -------------------------------------------------------------------------
    // Scenario disabled
    // -------------------------------------------------------------------------

    @Test
    void disabled_runDoesNothing() {
        ConfigRegressionScenario scenario = scenarioWithValue(0); // bad value, but flag is off
        assertThatNoException().isThrownBy(() -> scenario.run("INC-004"));
    }

    // -------------------------------------------------------------------------
    // Scenario enabled — known-good config
    // -------------------------------------------------------------------------

    @Test
    void enabled_knownGoodValue_doesNotThrow() {
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        ConfigRegressionScenario scenario = scenarioWithValue(ConfigRegressionScenario.KNOWN_GOOD_MAX);

        assertThatNoException().isThrownBy(() -> scenario.run("INC-004"));
    }

    // -------------------------------------------------------------------------
    // Scenario enabled — regression config (max-items-per-order = 0)
    // -------------------------------------------------------------------------

    @Test
    void enabled_zeroMaxItems_throwsSimulationException() {
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        ConfigRegressionScenario scenario = scenarioWithValue(0);

        assertThatThrownBy(() -> scenario.run("INC-004"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-004")
                .hasMessageContaining("max-items-per-order")
                .hasMessageContaining("0");
    }

    @Test
    void enabled_negativeMaxItems_throwsSimulationException() {
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        ConfigRegressionScenario scenario = scenarioWithValue(-1);

        assertThatThrownBy(() -> scenario.run("INC-004"))
                .isInstanceOf(SimulationException.class);
    }

    @Test
    void enabled_zeroMaxItems_exceptionMentionsKnownGoodValue() {
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        ConfigRegressionScenario scenario = scenarioWithValue(0);

        assertThatThrownBy(() -> scenario.run("INC-004"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining(String.valueOf(ConfigRegressionScenario.KNOWN_GOOD_MAX));
    }

    // -------------------------------------------------------------------------
    // Reset lifecycle
    // -------------------------------------------------------------------------

    @Test
    void afterReset_regressionConfigNoLongerThrows() {
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        ConfigRegressionScenario scenario = scenarioWithValue(0);

        // First: would throw.
        assertThatThrownBy(() -> scenario.run("INC-004"))
                .isInstanceOf(SimulationException.class);

        // Reset.
        SimulationState.resetAll();

        // Second: must be a no-op.
        assertThatNoException().isThrownBy(() -> scenario.run("INC-004"));
    }

    @Test
    void getMaxItemsPerOrder_reflectsInjectedValue() {
        ConfigRegressionScenario scenario = scenarioWithValue(42);
        assertThat(scenario.getMaxItemsPerOrder()).isEqualTo(42);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ConfigRegressionScenario scenarioWithValue(int maxItems) {
        ConfigRegressionScenario s = new ConfigRegressionScenario();
        s.setMaxItemsPerOrder(maxItems);
        return s;
    }
}
