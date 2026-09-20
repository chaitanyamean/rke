package com.rke.backend.simulation.scenario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rke.backend.simulation.IncidentId;
import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * INC-002 — Slow PostgreSQL Query.
 *
 * <p>Uses PostgreSQL's built-in {@code pg_sleep()} function to introduce a
 * deterministic, database-level delay.  This produces realistic DB-span
 * durations in traces (once tracing is added) and measurable latency in
 * normal application logs.
 *
 * <p>Observed symptoms:
 * <ul>
 *   <li>Increased DB span duration (visible in future trace data).</li>
 *   <li>Increased API response latency.</li>
 *   <li>WARNING log with elapsed duration.</li>
 *   <li>HTTP 200 (the slow query completes) — not a failure, just latency.</li>
 * </ul>
 *
 * <p>When {@link #SIMULATE_TIMEOUT} is set to true the scenario additionally
 * throws a {@link SimulationException} after the sleep completes, simulating a
 * query that was killed by an application-level timeout.
 */
@Component
public class SlowQueryScenario {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryScenario.class);

    /** Duration (seconds) passed to pg_sleep. */
    static final int SLEEP_SECONDS = 5;

    /**
     * When {@code true}, the scenario throws after the sleep to simulate a
     * query timeout.  When {@code false} it returns normally (latency only).
     */
    static final boolean SIMULATE_TIMEOUT = true;

    private final DataSource dataSource;

    public SlowQueryScenario(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Executes {@code SELECT pg_sleep(?)} against the live database, then
     * optionally throws a timeout exception.
     *
     * @param incidentId incident identifier embedded in logs and exceptions.
     * @throws SimulationException when {@link #SIMULATE_TIMEOUT} is {@code true}.
     */
    public void run(String incidentId) {

        if (!SimulationState.isActive(SimulationState.SLOW_QUERY)) {
            log.debug("[{}] Slow query simulation is not active — skipping.", incidentId);
            return;
        }

        log.warn("[{}] Slow query scenario starting: executing pg_sleep({}) against database",
                incidentId, SLEEP_SECONDS);

        long start = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT pg_sleep(?)")) {

            ps.setInt(1, SLEEP_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                // consume result to ensure the sleep completes fully
                if (rs.next()) {
                    log.debug("[{}] pg_sleep returned: {}", incidentId, rs.getString(1));
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.warn("[{}] Slow query completed in {} ms — simulated DB latency spike",
                    incidentId, elapsed);

            if (SIMULATE_TIMEOUT) {
                throw new SimulationException(incidentId,
                        "Simulated query timeout: database operation took " + elapsed
                                + " ms, exceeding the configured statement timeout threshold.");
            }

        } catch (SimulationException e) {
            throw e; // re-throw — don't wrap in another SimulationException
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[{}] Slow query scenario failed unexpectedly after {} ms: {}",
                    incidentId, elapsed, e.getMessage());
            throw new SimulationException(incidentId,
                    "Slow query scenario encountered an unexpected error after " + elapsed + " ms.", e);
        }
    }
}
