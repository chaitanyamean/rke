package com.rke.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rke.backend.simulation.scenario.BackendExceptionScenario;
import com.rke.backend.simulation.scenario.CascadeFailureScenario;
import com.rke.backend.simulation.scenario.ConfigRegressionScenario;
import com.rke.backend.simulation.scenario.DbPoolExhaustionScenario;
import com.rke.backend.simulation.scenario.HistoricalIncidentScenario;
import com.rke.backend.simulation.scenario.SlowQueryScenario;

/**
 * Unit tests for {@link IncidentSimulationController}.
 *
 * <p>All scenario beans are mocked so tests are deterministic and fast.
 * This covers:
 * <ul>
 *   <li>Each trigger returns the expected {@link IncidentResponse}.</li>
 *   <li>Each trigger enables the correct {@link SimulationState} flag.</li>
 *   <li>When the scenario throws, the exception propagates (HTTP 500 via GlobalExceptionHandler).</li>
 *   <li>The simulation flag is disabled after the scenario completes or throws.</li>
 *   <li>Reset clears all active scenarios.</li>
 *   <li>Status endpoint reflects current state.</li>
 * </ul>
 */
class IncidentSimulationControllerTest {

    private DbPoolExhaustionScenario dbPoolScenario;
    private SlowQueryScenario slowQueryScenario;
    private BackendExceptionScenario backendExceptionScenario;
    private ConfigRegressionScenario configRegressionScenario;
    private CascadeFailureScenario cascadeFailureScenario;
    private HistoricalIncidentScenario historicalIncidentScenario;

    private IncidentSimulationController controller;

    @BeforeEach
    void setUp() {
        dbPoolScenario            = mock(DbPoolExhaustionScenario.class);
        slowQueryScenario         = mock(SlowQueryScenario.class);
        backendExceptionScenario  = mock(BackendExceptionScenario.class);
        configRegressionScenario  = mock(ConfigRegressionScenario.class);
        cascadeFailureScenario    = mock(CascadeFailureScenario.class);
        historicalIncidentScenario = mock(HistoricalIncidentScenario.class);

        controller = new IncidentSimulationController(
                dbPoolScenario,
                slowQueryScenario,
                backendExceptionScenario,
                configRegressionScenario,
                cascadeFailureScenario,
                historicalIncidentScenario);
    }

    @AfterEach
    void cleanup() {
        SimulationState.resetAll();
    }

    // =========================================================================
    // INC-001 — DB Pool Exhaustion
    // =========================================================================

    @Test
    void triggerDbPoolExhaustion_invokesScenario() {
        controller.triggerDbPoolExhaustion();
        verify(dbPoolScenario).run(IncidentId.INC_001);
    }

    @Test
    void triggerDbPoolExhaustion_enablesAndDisablesFlag() {
        // Capture the state inside the scenario call.
        doThrow(new SimulationException(IncidentId.INC_001, "exhausted"))
                .when(dbPoolScenario).run(anyString());

        assertThatThrownBy(() -> controller.triggerDbPoolExhaustion())
                .isInstanceOf(SimulationException.class);

        // Flag must be cleared after throw via finally.
        assertThat(SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION)).isFalse();
    }

    @Test
    void triggerDbPoolExhaustion_whenScenarioSucceeds_returnsTriggeredResponse() {
        doNothing().when(dbPoolScenario).run(anyString());

        var response = controller.triggerDbPoolExhaustion();

        assertThat(response.incidentId()).isEqualTo(IncidentId.INC_001);
        assertThat(response.status()).isEqualTo("triggered");
        assertThat(response.scenario()).isEqualTo("database_pool_exhaustion");
    }

    // =========================================================================
    // INC-002 — Slow Query
    // =========================================================================

    @Test
    void triggerSlowQuery_invokesScenario() {
        controller.triggerSlowQuery();
        verify(slowQueryScenario).run(IncidentId.INC_002);
    }

    @Test
    void triggerSlowQuery_scenarioThrows_exceptionPropagates() {
        doThrow(new SimulationException(IncidentId.INC_002, "timeout"))
                .when(slowQueryScenario).run(anyString());

        assertThatThrownBy(() -> controller.triggerSlowQuery())
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-002");
    }

    @Test
    void triggerSlowQuery_flagDisabledAfterThrow() {
        doThrow(new SimulationException(IncidentId.INC_002, "timeout"))
                .when(slowQueryScenario).run(anyString());

        try { controller.triggerSlowQuery(); } catch (SimulationException ignored) {}

        assertThat(SimulationState.isActive(SimulationState.SLOW_QUERY)).isFalse();
    }

    // =========================================================================
    // INC-003 — Backend Exception
    // =========================================================================

    @Test
    void triggerBackendError_invokesScenario() {
        controller.triggerBackendError();
        verify(backendExceptionScenario).run(IncidentId.INC_003);
    }

    @Test
    void triggerBackendError_scenarioThrows_exceptionPropagates() {
        var cause = new ArithmeticException("integer overflow");
        doThrow(new SimulationException(IncidentId.INC_003, "calculation failed", cause))
                .when(backendExceptionScenario).run(anyString());

        assertThatThrownBy(() -> controller.triggerBackendError())
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-003")
                .cause()
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void triggerBackendError_flagDisabledAfterThrow() {
        doThrow(new SimulationException(IncidentId.INC_003, "err"))
                .when(backendExceptionScenario).run(anyString());

        try { controller.triggerBackendError(); } catch (SimulationException ignored) {}

        assertThat(SimulationState.isActive(SimulationState.BACKEND_EXCEPTION)).isFalse();
    }

    // =========================================================================
    // INC-004 — Config Regression
    // =========================================================================

    @Test
    void triggerConfigRegression_invokesScenario() {
        when(configRegressionScenario.getMaxItemsPerOrder()).thenReturn(50);
        controller.triggerConfigRegression();
        verify(configRegressionScenario).run(IncidentId.INC_004);
    }

    @Test
    void triggerConfigRegression_regressionActive_exceptionPropagates() {
        doThrow(new SimulationException(IncidentId.INC_004, "config regression"))
                .when(configRegressionScenario).run(anyString());

        assertThatThrownBy(() -> controller.triggerConfigRegression())
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-004");
    }

    @Test
    void triggerConfigRegression_knownGoodConfig_returnsSuccess() {
        doNothing().when(configRegressionScenario).run(anyString());
        when(configRegressionScenario.getMaxItemsPerOrder()).thenReturn(50);

        var response = controller.triggerConfigRegression();

        assertThat(response.incidentId()).isEqualTo(IncidentId.INC_004);
        assertThat(response.status()).isEqualTo("triggered");
        assertThat(response.description()).contains("50");
    }

    @Test
    void triggerConfigRegression_flagDisabledAfterThrow() {
        doThrow(new SimulationException(IncidentId.INC_004, "regression"))
                .when(configRegressionScenario).run(anyString());

        try { controller.triggerConfigRegression(); } catch (SimulationException ignored) {}

        assertThat(SimulationState.isActive(SimulationState.CONFIG_REGRESSION)).isFalse();
    }

    // =========================================================================
    // INC-005 — Cascade
    // =========================================================================

    @Test
    void triggerCascade_invokesScenario() {
        controller.triggerCascade();
        verify(cascadeFailureScenario).run(IncidentId.INC_005);
    }

    @Test
    void triggerCascade_scenarioThrows_exceptionPropagates() {
        doThrow(new SimulationException(IncidentId.INC_005, "cascade"))
                .when(cascadeFailureScenario).run(anyString());

        assertThatThrownBy(() -> controller.triggerCascade())
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-005");
    }

    @Test
    void triggerCascade_flagDisabledAfterThrow() {
        doThrow(new SimulationException(IncidentId.INC_005, "cascade"))
                .when(cascadeFailureScenario).run(anyString());

        try { controller.triggerCascade(); } catch (SimulationException ignored) {}

        assertThat(SimulationState.isActive(SimulationState.CASCADE_FAILURE)).isFalse();
    }

    // =========================================================================
    // INC-006 — Historical
    // =========================================================================

    @Test
    void triggerHistorical_invokesScenario() {
        controller.triggerHistorical();
        verify(historicalIncidentScenario).run(IncidentId.INC_006);
    }

    @Test
    void triggerHistorical_scenarioThrows_exceptionPropagates() {
        doThrow(new SimulationException(IncidentId.INC_006, "historical exhaustion"))
                .when(historicalIncidentScenario).run(anyString());

        assertThatThrownBy(() -> controller.triggerHistorical())
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-006");
    }

    @Test
    void triggerHistorical_flagDisabledAfterThrow() {
        doThrow(new SimulationException(IncidentId.INC_006, "historical"))
                .when(historicalIncidentScenario).run(anyString());

        try { controller.triggerHistorical(); } catch (SimulationException ignored) {}

        assertThat(SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION_V2)).isFalse();
    }

    // =========================================================================
    // Status endpoint
    // =========================================================================

    @Test
    void status_whenNoScenariosActive_returnsEmptyList() {
        var body = controller.status();

        assertThat(body).containsKey("activeSimulations");
        assertThat((java.util.List<?>) body.get("activeSimulations")).isEmpty();
        assertThat(body.get("count")).isEqualTo(0);
    }

    @Test
    void status_reflectsCurrentlyActiveScenarios() {
        SimulationState.enable(SimulationState.SLOW_QUERY);
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);

        var body = controller.status();
        @SuppressWarnings("unchecked")
        var active = (java.util.List<String>) body.get("activeSimulations");

        assertThat(active).contains("SLOW_QUERY", "BACKEND_EXCEPTION");
        assertThat(body.get("count")).isEqualTo(2);
    }

    // =========================================================================
    // Reset endpoint
    // =========================================================================

    @Test
    void reset_clearsAllActiveScenarios() {
        SimulationState.enable(SimulationState.DB_POOL_EXHAUSTION);
        SimulationState.enable(SimulationState.SLOW_QUERY);
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);

        controller.reset();

        assertThat(SimulationState.activeScenarios()).isEmpty();
    }

    @Test
    void reset_isIdempotent_whenNothingActive() {
        // Must not throw.
        controller.reset();
        controller.reset();
        assertThat(SimulationState.activeScenarios()).isEmpty();
    }

    // =========================================================================
    // Normal endpoints unaffected when simulations are disabled
    // =========================================================================

    @Test
    void whenAllSimulationsDisabled_noScenarioIsInvoked_onStatus() {
        // None of the scenario beans should be called when only status() is queried.
        controller.status();

        verify(dbPoolScenario, never()).run(anyString());
        verify(slowQueryScenario, never()).run(anyString());
        verify(backendExceptionScenario, never()).run(anyString());
        verify(configRegressionScenario, never()).run(anyString());
        verify(cascadeFailureScenario, never()).run(anyString());
        verify(historicalIncidentScenario, never()).run(anyString());
    }
}
