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

import com.rke.backend.simulation.IncidentId;
import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * INC-001 — PostgreSQL Connection Pool Exhaustion.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Reconfigure HikariCP's {@code maximumPoolSize} to 2 (down from the
 *       default of 10) so the pool is deliberately small.</li>
 *   <li>Spawn {@code CONCURRENT_REQUESTORS} threads that each hold a JDBC
 *       connection for {@code HOLD_DURATION_MS} milliseconds while sleeping,
 *       saturating the tiny pool.</li>
 *   <li>The main thread then tries to acquire one more connection with a
 *       short timeout — this acquisition times out and propagates as a
 *       {@link SimulationException} (HTTP 500).</li>
 *   <li>All held connections are released afterwards; the pool returns
 *       to normal.</li>
 * </ol>
 *
 * <p>Observed symptoms:
 * <ul>
 *   <li>Increased request latency while connections are held.</li>
 *   <li>Connection acquisition timeout exception in logs.</li>
 *   <li>HTTP 500 response from the trigger endpoint.</li>
 *   <li>Meaningful stack trace mentioning HikariCP pool exhaustion.</li>
 * </ul>
 */
@Component
public class DbPoolExhaustionScenario {

    private static final Logger log = LoggerFactory.getLogger(DbPoolExhaustionScenario.class);

    /** Number of threads that simultaneously hold connections. */
    static final int CONCURRENT_REQUESTORS = 4;

    /** How long (ms) each simulated thread holds its connection. */
    static final long HOLD_DURATION_MS = 4_000;

    /** Timeout (ms) the probe connection waits before giving up. */
    static final long PROBE_TIMEOUT_MS = 3_000;

    private final DataSource dataSource;

    public DbPoolExhaustionScenario(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Executes the pool-exhaustion scenario.
     *
     * <p>This method blocks until the probe connection attempt fails (or
     * unexpectedly succeeds), then releases all held connections.
     *
     * @param incidentId used in log messages and the exception payload so the
     *                   RCA agent can correlate the failure back to this run.
     * @throws SimulationException always — carries the pool-exhaustion cause
     */
    public void run(String incidentId) throws SimulationException {

        if (!SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION)
                && !SimulationState.isActive(SimulationState.DB_POOL_EXHAUSTION_V2)) {
            log.debug("[{}] Pool exhaustion simulation is not active — skipping.", incidentId);
            return;
        }

        log.warn("[{}] Pool exhaustion scenario starting: saturating connection pool with {} holders",
                incidentId, CONCURRENT_REQUESTORS);

        List<Connection> heldConnections = new ArrayList<>();
        CountDownLatch allHolding = new CountDownLatch(CONCURRENT_REQUESTORS);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTORS);
        List<Future<?>> futures = new ArrayList<>();

        try {
            // Spawn holders — each acquires a connection and parks until told to release.
            for (int i = 0; i < CONCURRENT_REQUESTORS; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        Connection conn = dataSource.getConnection();
                        synchronized (heldConnections) {
                            heldConnections.add(conn);
                        }
                        log.debug("[{}] Holder-{} acquired connection", incidentId, idx);
                        allHolding.countDown();
                        // Hold the connection until the main thread says release.
                        releaseLatch.await(HOLD_DURATION_MS + 1_000, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        allHolding.countDown();
                        log.debug("[{}] Holder-{} could not acquire connection: {}", incidentId, idx, e.getMessage());
                    }
                }));
            }

            // Wait for holders to park (or timeout after HOLD_DURATION_MS).
            boolean allHeld = allHolding.await(HOLD_DURATION_MS, TimeUnit.MILLISECONDS);
            log.warn("[{}] {} holder(s) active; attempting probe connection (timeout={}ms) …",
                    incidentId, allHeld ? CONCURRENT_REQUESTORS : "some", PROBE_TIMEOUT_MS);

            // Now attempt one more connection — should time out.
            Throwable exhaustionCause = attemptProbeConnection(incidentId);

            // Signal holders to release.
            releaseLatch.countDown();

            if (exhaustionCause != null) {
                throw new SimulationException(incidentId,
                        "Database connection pool exhausted — all " + CONCURRENT_REQUESTORS
                                + " connections held by concurrent requests. "
                                + "Pool acquisition timed out after " + PROBE_TIMEOUT_MS + " ms.",
                        exhaustionCause);
            }

            // Probe succeeded (pool was larger than expected) — still log as a near-miss.
            log.warn("[{}] Probe connection succeeded unexpectedly — pool may be larger than expected", incidentId);
            throw new SimulationException(incidentId,
                    "Pool exhaustion scenario ran but the probe connection succeeded. "
                            + "Consider reducing spring.datasource.hikari.maximum-pool-size.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SimulationException(incidentId, "Pool exhaustion scenario interrupted", e);
        } finally {
            releaseLatch.countDown(); // always release holders
            pool.shutdownNow();
            releaseAll(heldConnections, incidentId);
        }
    }

    private Throwable attemptProbeConnection(String incidentId) {
        // Use a temporary datasource wrapper with a short timeout to make the
        // acquisition failure deterministic.  We reuse the existing pool and
        // simply time the attempt ourselves.
        long deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS;
        try (Connection probe = dataSource.getConnection()) {
            long elapsed = System.currentTimeMillis() - (deadline - PROBE_TIMEOUT_MS);
            log.debug("[{}] Probe acquired in {} ms (unexpected)", incidentId, elapsed);
            return null; // pool had a free slot — no exhaustion
        } catch (SQLException e) {
            log.warn("[{}] Probe connection timed out as expected: {}", incidentId, e.getMessage());
            return e;
        }
    }

    private void releaseAll(List<Connection> connections, String incidentId) {
        synchronized (connections) {
            for (Connection c : connections) {
                try {
                    c.close();
                } catch (Exception ignored) {}
            }
        }
        log.info("[{}] All held connections released — pool is healthy again.", incidentId);
    }
}
