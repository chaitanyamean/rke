package com.rke.backend.simulation.scenario;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * INC-003 — Backend Application Exception / HTTP 500.
 *
 * <p>Simulates a realistic internal application failure: a service method that
 * receives a request, performs partial validation, and then encounters a
 * domain-layer assertion it cannot recover from.  The exception propagates
 * through the full Spring MVC exception-handling chain (including
 * {@link com.rke.backend.exception.GlobalExceptionHandler}) before returning
 * an HTTP 500 with a structured JSON body.
 *
 * <p>The failure path is deliberately non-trivial:
 * <ol>
 *   <li>A request correlation ID is generated and bound to the log context.</li>
 *   <li>A fake "inventory check" succeeds.</li>
 *   <li>A fake "price calculation" produces an arithmetic overflow.</li>
 *   <li>The overflow triggers an {@link ArithmeticException} which is caught
 *       and wrapped in a {@link SimulationException}.</li>
 * </ol>
 *
 * <p>This path exercises:
 * <ul>
 *   <li>Multi-step service execution with partial success before failure.</li>
 *   <li>Exception wrapping and propagation.</li>
 *   <li>Normal Spring exception-handling plumbing.</li>
 *   <li>Meaningful stack trace in server logs.</li>
 * </ul>
 *
 * <p>Observed symptoms:
 * <ul>
 *   <li>HTTP 500 with {@code simulatedFailure: true} in the JSON body.</li>
 *   <li>ERROR-level log with full stack trace.</li>
 *   <li>Request correlation ID in both the response body and the logs.</li>
 * </ul>
 */
@Component
public class BackendExceptionScenario {

    private static final Logger log = LoggerFactory.getLogger(BackendExceptionScenario.class);

    /**
     * Executes the simulated failure path.
     *
     * @param incidentId incident identifier propagated through logs and the response.
     * @throws SimulationException always — wraps an {@link ArithmeticException} to
     *                             simulate a realistic price-calculation overflow.
     */
    public void run(String incidentId) {

        if (!SimulationState.isActive(SimulationState.BACKEND_EXCEPTION)) {
            log.debug("[{}] Backend exception simulation is not active — skipping.", incidentId);
            return;
        }

        String correlationId = UUID.randomUUID().toString();

        log.info("[{}] correlationId={} Processing order — starting inventory check", incidentId, correlationId);

        // Step 1: inventory check (succeeds)
        boolean inStock = simulateInventoryCheck(correlationId);
        log.info("[{}] correlationId={} Inventory check passed: inStock={}", incidentId, correlationId, inStock);

        // Step 2: price calculation (fails with arithmetic overflow)
        log.info("[{}] correlationId={} Starting price calculation …", incidentId, correlationId);
        try {
            simulatePriceCalculationOverflow(correlationId);
        } catch (ArithmeticException e) {
            log.error("[{}] correlationId={} Price calculation failed: {}",
                    incidentId, correlationId, e.getMessage(), e);
            throw new SimulationException(incidentId,
                    "Internal error during price calculation for order correlationId=" + correlationId
                            + ". Arithmetic overflow in discount computation: " + e.getMessage(),
                    e);
        }
    }

    // -------------------------------------------------------------------------
    // Private simulation helpers — each mimics a real service sub-step
    // -------------------------------------------------------------------------

    private boolean simulateInventoryCheck(String correlationId) {
        // Deterministic "success" — item is in stock.
        log.debug("correlationId={} inventory check: sku=ITEM-42 qty=3 available=true", correlationId);
        return true;
    }

    private void simulatePriceCalculationOverflow(String correlationId) {
        // Integer overflow: the wrong type was used in an accumulation loop —
        // a realistic bug that developers actually introduce.
        int runningTotal = Integer.MAX_VALUE;
        int lineAmount   = 1;
        int result       = Math.addExact(runningTotal, lineAmount); // always throws
        log.debug("correlationId={} computed total: {}", correlationId, result);
    }
}
