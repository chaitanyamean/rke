package com.rke.backend.simulation.scenario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * INC-006 — Historical Similar Incident.
 *
 * <p>A second occurrence of a connection-pool exhaustion scenario, deliberately
 * similar to INC-001 but not byte-for-byte identical:
 *
 * <ul>
 *   <li>Fewer concurrent holders ({@value #CONCURRENT_REQUESTORS} vs. INC-001's 4).</li>
 *   <li>Longer hold duration ({@value #HOLD_DURATION_MS} ms vs. INC-001's 4 000 ms).</li>
 *   <li>Distinct log message wording mentioning "variant" and "INC-006".</li>
 *   <li>Uses the {@link SimulationState#DB_POOL_EXHAUSTION_V2} state flag.</li>
 * </ul>
 *
 * <p>Purpose: when the RCA agent processes INC-006 it should:
 * <ol>
 *   <li>Observe connection-pool exhaustion symptoms.</li>
 *   <li>Query its incident memory.</li>
 *   <li>Find INC-001 as a similar historical incident.</li>
 *   <li>Reuse INC-001's resolution evidence in its report.</li>
 * </ol>
 *
 * <p>Observed symptoms (same as INC-001, different magnitude):
 * <ul>
 *   <li>Connection pool exhausted after fewer concurrent holders.</li>
 *   <li>HTTP 500 from the trigger endpoint.</li>
 *   <li>Log message contains "INC-006" and "historical" for easy regex filtering.</li>
 * </ul>
 */
@Component
public class HistoricalIncidentScenario {

    private static final Logger log = LoggerFactory.getLogger(HistoricalIncidentScenario.class);

    /** Fewer holders than INC-001 — the pool is slightly less saturated. */
    static final int CONCURRENT_REQUESTORS = 3;

    /** Longer hold to simulate a more persistent congestion window. */
    static final long HOLD_DURATION_MS = 6_000;

    /** Probe timeout — still shorter than hold so it expires. */
    static final long PROBE_TIMEOUT_MS = 2_500;

    private final DataSource dataSource;

    public HistoricalIncidentScenario(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Executes the historical variant of the pool-exhaustion scenario.
     *
     * @param incidentId incident identifier (INC-006) for log correlation.
     * @throws SimulationException always — carries the pool-exhaustion cause.
     */
    public void run(String incidentId) {

        if (!SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION_V2)) {
            log.debug("[{}] Historical pool exhaustion simulation is not active — skipping.", incidentId);
            return;
        }

        log.warn("[{}] Historical pool exhaustion variant (INC-006): " +
                        "saturating pool with {} holders for up to {} ms",
                incidentId, CONCURRENT_REQUESTORS, HOLD_DURATION_MS);

        List<Connection> heldConnections = new ArrayList<>();
        CountDownLatch allHolding = new CountDownLatch(CONCURRENT_REQUESTORS);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTORS);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_REQUESTORS; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        Connection conn = dataSource.getConnection();
                        synchronized (heldConnections) { heldConnections.add(conn); }
                        log.debug("[{}] historical holder-{} acquired connection", incidentId, idx);
                        allHolding.countDown();
                        releaseLatch.await(HOLD_DURATION_MS + 1_000, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        allHolding.countDown();
                        log.debug("[{}] historical holder-{} could not acquire: {}", incidentId, idx, e.getMessage());
                    }
                }));
            }

            allHolding.await(HOLD_DURATION_MS, TimeUnit.MILLISECONDS);
            log.warn("[{}] historical variant: {} holders active, attempting probe …",
                    incidentId, CONCURRENT_REQUESTORS);

            Throwable exhaustionCause = attemptProbe(incidentId);
            releaseLatch.countDown();

            if (exhaustionCause != null) {
                throw new SimulationException(incidentId,
                        "INC-006 historical variant: database connection pool exhausted. "
                                + CONCURRENT_REQUESTORS + " connections held; probe timed out after "
                                + PROBE_TIMEOUT_MS + " ms. Similar to INC-001 — see historical incident memory.",
                        exhaustionCause);
            }

            log.warn("[{}] historical variant: probe succeeded unexpectedly", incidentId);
            throw new SimulationException(incidentId,
                    "INC-006 historical variant: probe connection succeeded. Pool may be larger than expected.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SimulationException(incidentId, "INC-006 interrupted", e);
        } finally {
            releaseLatch.countDown();
            pool.shutdownNow();
            releaseAll(heldConnections, incidentId);
        }
    }

    private Throwable attemptProbe(String incidentId) {
        try (Connection probe = dataSource.getConnection()) {
            log.debug("[{}] historical probe acquired (unexpected)", incidentId);
            return null;
        } catch (SQLException e) {
            log.warn("[{}] historical probe timed out as expected: {}", incidentId, e.getMessage());
            return e;
        }
    }

    private void releaseAll(List<Connection> conns, String incidentId) {
        synchronized (conns) {
            for (Connection c : conns) {
                try { c.close(); } catch (Exception ignored) {}
            }
        }
        log.info("[{}] historical variant: all held connections released.", incidentId);
    }
}
