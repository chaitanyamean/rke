package com.rke.backend.simulation.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * INC-005 — Cascading Failure.
 *
 * <p>Simulates a multi-layer dependency failure without introducing any real
 * microservices.  The chain is:
 *
 * <pre>
 *   HTTP Client
 *       ↓
 *   IncidentSimulationController  (upstream — receives the API call)
 *       ↓
 *   CascadeFailureScenario        (application layer)
 *       ↓
 *   PricingServiceStub            (downstream internal dependency)
 *       ↓
 *   RatingEngineStub              (deep dependency)
 *       ↓
 *   IOException (simulated network timeout)
 * </pre>
 *
 * <p>Each layer catches the downstream exception, logs it at ERROR level
 * (so the logs show the full cascade), and re-wraps it with additional
 * context before propagating upward.  The final {@link SimulationException}
 * that reaches the controller carries the full cause chain.
 *
 * <p>Observed symptoms:
 * <ul>
 *   <li>Three nested log lines, each from a different "service", showing
 *       downstream → upstream propagation.</li>
 *   <li>Increased latency (each layer sleeps briefly to simulate I/O wait).</li>
 *   <li>HTTP 500 with a message that references the cascade origin.</li>
 *   <li>The response body {@code simulatedFailure: true} distinguishes this
 *       from a real incident during investigation.</li>
 * </ul>
 */
@Component
public class CascadeFailureScenario {

    private static final Logger log = LoggerFactory.getLogger(CascadeFailureScenario.class);

    /** Simulated I/O latency per layer (ms). */
    static final long LAYER_LATENCY_MS = 500;

    /**
     * Executes the cascading failure.
     *
     * @param incidentId incident identifier for log correlation.
     * @throws SimulationException always — wraps the full cascading cause chain.
     */
    public void run(String incidentId) {

        if (!SimulationState.isActive(SimulationState.CASCADE_FAILURE)) {
            log.debug("[{}] Cascade failure simulation is not active — skipping.", incidentId);
            return;
        }

        log.info("[{}] [IncidentController] Dispatching order to PricingService …", incidentId);

        try {
            callPricingService(incidentId);
        } catch (Exception e) {
            // Upstream layer: catches the downstream failure and re-wraps.
            log.error("[{}] [IncidentController] Upstream failure: PricingService unavailable — " +
                            "order cannot be processed: {}",
                    incidentId, e.getMessage(), e);
            throw new SimulationException(incidentId,
                    "Upstream failure: PricingService call failed due to cascading dependency error. "
                            + "Root cause: " + rootMessage(e),
                    e);
        }
    }

    // -------------------------------------------------------------------------
    // Simulated downstream layers
    // -------------------------------------------------------------------------

    private void callPricingService(String incidentId) throws Exception {
        sleep(LAYER_LATENCY_MS);
        log.warn("[{}] [PricingService] Requesting rate from RatingEngine …", incidentId);
        try {
            callRatingEngine(incidentId);
        } catch (Exception e) {
            log.error("[{}] [PricingService] Downstream failure from RatingEngine: {}",
                    incidentId, e.getMessage());
            throw new RuntimeException(
                    "[PricingService] Failed to compute price: RatingEngine returned error", e);
        }
    }

    private void callRatingEngine(String incidentId) throws Exception {
        sleep(LAYER_LATENCY_MS);
        log.warn("[{}] [RatingEngine] Connecting to external rate feed …", incidentId);
        sleep(LAYER_LATENCY_MS);
        // Simulate a network timeout in the deepest dependency.
        log.error("[{}] [RatingEngine] Connection to external rate feed timed out after {} ms",
                incidentId, LAYER_LATENCY_MS);
        throw new java.io.IOException(
                "[RatingEngine] Connection timeout reaching external rate feed (simulated)");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
