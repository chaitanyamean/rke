package com.rke.backend.simulation;

/**
 * Well-known incident identifiers.
 *
 * <p>Each constant maps to a deterministic failure scenario that can be
 * triggered via the {@code /api/test/incidents/*} endpoints (dev/test only).
 */
public final class IncidentId {

    public static final String INC_001 = "INC-001";
    public static final String INC_002 = "INC-002";
    public static final String INC_003 = "INC-003";
    public static final String INC_004 = "INC-004";
    public static final String INC_005 = "INC-005";
    public static final String INC_006 = "INC-006";

    private IncidentId() {}
}
