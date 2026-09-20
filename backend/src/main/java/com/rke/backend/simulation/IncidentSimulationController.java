package com.rke.backend.simulation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.simulation.scenario.BackendExceptionScenario;
import com.rke.backend.simulation.scenario.CascadeFailureScenario;
import com.rke.backend.simulation.scenario.ConfigRegressionScenario;
import com.rke.backend.simulation.scenario.DbPoolExhaustionScenario;
import com.rke.backend.simulation.scenario.HistoricalIncidentScenario;
import com.rke.backend.simulation.scenario.SlowQueryScenario;

/**
 * Failure-injection API — <strong>dev/test only</strong>.
 *
 * <p>This controller is activated by the {@code dev} Spring profile
 * ({@code @Profile("dev")}).  It is <em>never</em> registered in the
 * Spring context when the {@code prod} profile is active, so none of
 * its endpoints exist at runtime in production.
 *
 * <h3>Base path</h3>
 * {@code /api/test/incidents}
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST   /api/test/incidents/db-pool-exhaustion   → INC-001
 *   POST   /api/test/incidents/slow-query           → INC-002
 *   POST   /api/test/incidents/backend-error        → INC-003
 *   POST   /api/test/incidents/config-regression    → INC-004
 *   POST   /api/test/incidents/cascade              → INC-005
 *   POST   /api/test/incidents/historical           → INC-006
 *
 *   DELETE /api/test/incidents/reset                → reset all active simulations
 *   GET    /api/test/incidents/status               → list currently active simulations
 * </pre>
 *
 * <p>Each trigger endpoint:
 * <ol>
 *   <li>Enables the corresponding {@link SimulationState} flag.</li>
 *   <li>Executes the scenario synchronously so the caller receives the
 *       realistic failure HTTP response.</li>
 *   <li>Disables the flag on return (or on exception) so that subsequent
 *       requests through normal endpoints are unaffected.</li>
 * </ol>
 *
 * <p>The {@code reset} endpoint clears all flags at once, making it safe
 * to call after any scenario regardless of whether it completed normally.
 */
@RestController
@RequestMapping("/api/test/incidents")
@Profile("dev")
public class IncidentSimulationController {

    private static final Logger log = LoggerFactory.getLogger(IncidentSimulationController.class);

    private final DbPoolExhaustionScenario dbPoolExhaustionScenario;
    private final SlowQueryScenario slowQueryScenario;
    private final BackendExceptionScenario backendExceptionScenario;
    private final ConfigRegressionScenario configRegressionScenario;
    private final CascadeFailureScenario cascadeFailureScenario;
    private final HistoricalIncidentScenario historicalIncidentScenario;

    public IncidentSimulationController(
            DbPoolExhaustionScenario dbPoolExhaustionScenario,
            SlowQueryScenario slowQueryScenario,
            BackendExceptionScenario backendExceptionScenario,
            ConfigRegressionScenario configRegressionScenario,
            CascadeFailureScenario cascadeFailureScenario,
            HistoricalIncidentScenario historicalIncidentScenario) {
        this.dbPoolExhaustionScenario    = dbPoolExhaustionScenario;
        this.slowQueryScenario           = slowQueryScenario;
        this.backendExceptionScenario    = backendExceptionScenario;
        this.configRegressionScenario    = configRegressionScenario;
        this.cascadeFailureScenario      = cascadeFailureScenario;
        this.historicalIncidentScenario  = historicalIncidentScenario;
    }

    // -------------------------------------------------------------------------
    // Trigger endpoints
    // -------------------------------------------------------------------------

    /**
     * INC-001 — PostgreSQL Connection Pool Exhaustion.
     *
     * <p>Enables the scenario flag, saturates the connection pool with
     * concurrent holders, attempts a probe connection that times out, then
     * throws {@link SimulationException} which {@link com.rke.backend.exception.GlobalExceptionHandler}
     * maps to HTTP 500.
     *
     * <p>Expected response: {@code HTTP 500} with {@code simulatedFailure: true}.
     */
    @PostMapping("/db-pool-exhaustion")
    public IncidentResponse triggerDbPoolExhaustion() {
        log.warn("=== INCIDENT TRIGGER: {} — db-pool-exhaustion ===", IncidentId.INC_001);
        SimulationState.enable(SimulationState.DB_POOL_EXHAUSTION);
        try {
            dbPoolExhaustionScenario.run(IncidentId.INC_001);
        } finally {
            SimulationState.disable(SimulationState.DB_POOL_EXHAUSTION);
        }
        // If the scenario ran without throwing (shouldn't happen for this incident)
        // return a triggered response so the caller is still informed.
        return IncidentResponse.triggered(IncidentId.INC_001, "database_pool_exhaustion",
                "Pool exhaustion scenario executed — probe connection succeeded (pool may be larger than expected).");
    }

    /**
     * INC-002 — Slow PostgreSQL Query.
     *
     * <p>Executes {@code SELECT pg_sleep(5)} against the live database, then
     * throws a timeout exception.
     *
     * <p>Expected response: {@code HTTP 500} after ~5 s delay.
     */
    @PostMapping("/slow-query")
    public IncidentResponse triggerSlowQuery() {
        log.warn("=== INCIDENT TRIGGER: {} — slow-query ===", IncidentId.INC_002);
        SimulationState.enable(SimulationState.SLOW_QUERY);
        try {
            slowQueryScenario.run(IncidentId.INC_002);
        } finally {
            SimulationState.disable(SimulationState.SLOW_QUERY);
        }
        return IncidentResponse.triggered(IncidentId.INC_002, "slow_query",
                "Slow query scenario executed — pg_sleep completed without timeout (SIMULATE_TIMEOUT=false).");
    }

    /**
     * INC-003 — Backend Application Exception.
     *
     * <p>Runs through a realistic multi-step service path that ends in an
     * {@link ArithmeticException} wrapped as a {@link SimulationException}.
     *
     * <p>Expected response: {@code HTTP 500} immediately.
     */
    @PostMapping("/backend-error")
    public IncidentResponse triggerBackendError() {
        log.warn("=== INCIDENT TRIGGER: {} — backend-error ===", IncidentId.INC_003);
        SimulationState.enable(SimulationState.BACKEND_EXCEPTION);
        try {
            backendExceptionScenario.run(IncidentId.INC_003);
        } finally {
            SimulationState.disable(SimulationState.BACKEND_EXCEPTION);
        }
        return IncidentResponse.triggered(IncidentId.INC_003, "backend_exception",
                "Backend exception scenario ran without throwing (unexpected).");
    }

    /**
     * INC-004 — Configuration Regression.
     *
     * <p>Checks {@code simulation.config-regression.max-items-per-order}.
     * When the value is {@code ≤ 0} (set in {@code application-simulation.yml})
     * the scenario throws; otherwise it returns 200.
     *
     * <p>Expected response: {@code HTTP 500} when the simulation profile is
     * active with the bad config value; {@code HTTP 200} with known-good config.
     */
    @PostMapping("/config-regression")
    public IncidentResponse triggerConfigRegression() {
        log.warn("=== INCIDENT TRIGGER: {} — config-regression ===", IncidentId.INC_004);
        SimulationState.enable(SimulationState.CONFIG_REGRESSION);
        try {
            configRegressionScenario.run(IncidentId.INC_004);
        } finally {
            SimulationState.disable(SimulationState.CONFIG_REGRESSION);
        }
        return IncidentResponse.triggered(IncidentId.INC_004, "config_regression",
                "Configuration is within known-good range: max-items-per-order="
                        + configRegressionScenario.getMaxItemsPerOrder());
    }

    /**
     * INC-005 — Cascading Failure.
     *
     * <p>Runs a simulated three-layer dependency chain that fails at the
     * deepest layer and propagates upward.
     *
     * <p>Expected response: {@code HTTP 500} after ~1.5 s latency.
     */
    @PostMapping("/cascade")
    public IncidentResponse triggerCascade() {
        log.warn("=== INCIDENT TRIGGER: {} — cascade ===", IncidentId.INC_005);
        SimulationState.enable(SimulationState.CASCADE_FAILURE);
        try {
            cascadeFailureScenario.run(IncidentId.INC_005);
        } finally {
            SimulationState.disable(SimulationState.CASCADE_FAILURE);
        }
        return IncidentResponse.triggered(IncidentId.INC_005, "cascade_failure",
                "Cascade failure scenario ran without throwing (unexpected).");
    }

    /**
     * INC-006 — Historical Similar Incident.
     *
     * <p>A second occurrence of pool exhaustion with different parameters,
     * intended to let the RCA agent find INC-001 as a historical match.
     *
     * <p>Expected response: {@code HTTP 500} with {@code simulatedFailure: true}.
     */
    @PostMapping("/historical")
    public IncidentResponse triggerHistorical() {
        log.warn("=== INCIDENT TRIGGER: {} — historical pool exhaustion variant ===", IncidentId.INC_006);
        SimulationState.enable(SimulationState.DB_POOL_EXHAUSTION_V2);
        try {
            historicalIncidentScenario.run(IncidentId.INC_006);
        } finally {
            SimulationState.disable(SimulationState.DB_POOL_EXHAUSTION_V2);
        }
        return IncidentResponse.triggered(IncidentId.INC_006, "db_pool_exhaustion_v2",
                "Historical pool exhaustion variant ran without throwing (unexpected).");
    }

    // -------------------------------------------------------------------------
    // Status and reset endpoints
    // -------------------------------------------------------------------------

    /**
     * Returns which simulations are currently active.
     *
     * <p>Useful for verifying state before and after a trigger.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Set<SimulationState> active = SimulationState.activeScenarios();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activeSimulations", active.stream().map(Enum::name).toList());
        body.put("count", active.size());
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    /**
     * Resets all active simulations, returning the application to normal behaviour.
     *
     * <p>Safe to call at any time — idempotent, even when no simulations are active.
     */
    @DeleteMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        log.info("=== SIMULATION RESET: clearing all active incident scenarios ===");
        SimulationState.resetAll();
    }
}
