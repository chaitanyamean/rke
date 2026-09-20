package com.rke.backend.simulation;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Thread-safe registry that tracks which incident scenarios are currently active.
 *
 * <p>This is the single source of truth that all scenario beans consult
 * before injecting any failure. When a scenario is not listed here the
 * application behaves exactly as in production.
 *
 * <p>Stored purely in-memory — restarts always begin from a clean state.
 */
public enum SimulationState {

    /** INC-001 — database connection-pool exhaustion. */
    DB_POOL_EXHAUSTION,

    /** INC-002 — slow PostgreSQL query (controlled latency). */
    SLOW_QUERY,

    /** INC-003 — backend application exception / HTTP 500. */
    BACKEND_EXCEPTION,

    /** INC-004 — configuration regression. */
    CONFIG_REGRESSION,

    /** INC-005 — cascading dependency failure. */
    CASCADE_FAILURE,

    /** INC-006 — repeated pool-exhaustion variant (historical similar incident). */
    DB_POOL_EXHAUSTION_V2;

    // -------------------------------------------------------------------------
    // Static registry
    // -------------------------------------------------------------------------

    private static final Set<SimulationState> ACTIVE = new CopyOnWriteArraySet<>();

    public static void enable(SimulationState state) {
        ACTIVE.add(state);
    }

    public static void disable(SimulationState state) {
        ACTIVE.remove(state);
    }

    public static void resetAll() {
        ACTIVE.clear();
    }

    public static boolean isActive(SimulationState state) {
        return ACTIVE.contains(state);
    }

    public static Set<SimulationState> activeScenarios() {
        return EnumSet.copyOf(ACTIVE.isEmpty() ? EnumSet.noneOf(SimulationState.class) : ACTIVE);
    }
}
