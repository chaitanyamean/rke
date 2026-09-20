package com.rke.backend.simulation.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * INC-004 — Configuration Regression.
 *
 * <p>Simulates a realistic configuration regression: a property that was
 * introduced as a feature flag (maximum items per order) has its value
 * changed from the known-good default ({@code 50}) to a broken value
 * ({@code 0}).  Any request that involves creating or validating an order
 * now fails with a misleading "empty order" error rather than the real cause
 * (bad configuration).
 *
 * <h3>Representation as a Git-visible change</h3>
 * The regression is modelled at two levels:
 * <ol>
 *   <li>The {@code simulation.config-regression.max-items-per-order} property
 *       in {@code application.yml} can be set to {@code 0} to permanently
 *       enable the regression (the Git diff that the RCA agent correlates).</li>
 *   <li>The {@link SimulationState#CONFIG_REGRESSION} flag overrides the YAML
 *       value at runtime, so the scenario can be enabled/disabled via the
 *       trigger API without restarting the process.</li>
 * </ol>
 *
 * <p>Both levers must agree for the regression to be active in a test run.
 * The {@code application-simulation.yml} profile file sets the YAML value to
 * {@code 0} and is committed to Git as the "bad configuration" state.  To
 * enable the regression for an RCA test run:
 * <ol>
 *   <li>Commit/checkout {@code application-simulation.yml} with
 *       {@code max-items-per-order: 0}.</li>
 *   <li>Start the app with the {@code simulation} profile active.</li>
 *   <li>POST to {@code /api/test/incidents/config-regression}.</li>
 * </ol>
 *
 * <p>Observed symptoms:
 * <ul>
 *   <li>HTTP 500 with a message mentioning "zero maximum items" configuration.</li>
 *   <li>WARN log referencing the property and its current bad value.</li>
 *   <li>The Git diff of {@code application-simulation.yml} shows the change
 *       from {@code 50} to {@code 0}.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "simulation.config-regression")
public class ConfigRegressionScenario {

    private static final Logger log = LoggerFactory.getLogger(ConfigRegressionScenario.class);

    /** Known-good maximum items per order. */
    public static final int KNOWN_GOOD_MAX = 50;

    /** Value injected from configuration — defaults to known-good value. */
    private int maxItemsPerOrder = KNOWN_GOOD_MAX;

    public void setMaxItemsPerOrder(int maxItemsPerOrder) {
        this.maxItemsPerOrder = maxItemsPerOrder;
    }

    public int getMaxItemsPerOrder() {
        return maxItemsPerOrder;
    }

    /**
     * Validates the active configuration and throws if the regression is detected.
     *
     * @param incidentId incident identifier propagated through logs and response.
     * @throws SimulationException when {@code maxItemsPerOrder <= 0}.
     */
    public void run(String incidentId) {

        if (!SimulationState.isActive(SimulationState.CONFIG_REGRESSION)) {
            log.debug("[{}] Config regression simulation is not active — skipping.", incidentId);
            return;
        }

        log.warn("[{}] Config regression check: simulation.config-regression.max-items-per-order={}",
                incidentId, maxItemsPerOrder);

        if (maxItemsPerOrder <= 0) {
            log.error("[{}] CONFIGURATION REGRESSION DETECTED: max-items-per-order={} " +
                            "(expected > 0). Orders will be rejected as 'empty'. " +
                            "Check recent Git changes to application-simulation.yml.",
                    incidentId, maxItemsPerOrder);

            throw new SimulationException(incidentId,
                    "Configuration regression: simulation.config-regression.max-items-per-order="
                            + maxItemsPerOrder + ". "
                            + "This value was changed from the known-good value (" + KNOWN_GOOD_MAX
                            + ") and is causing all order validations to fail.");
        }

        // Known-good path — configuration is correct.
        log.info("[{}] Configuration is within known-good range: max-items-per-order={}",
                incidentId, maxItemsPerOrder);
    }
}
