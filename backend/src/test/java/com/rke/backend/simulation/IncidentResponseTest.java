package com.rke.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IncidentResponse} factory methods.
 */
class IncidentResponseTest {

    @Test
    void triggered_populatesAllFields() {
        var r = IncidentResponse.triggered("INC-001", "database_pool_exhaustion", "desc");

        assertThat(r.incidentId()).isEqualTo("INC-001");
        assertThat(r.scenario()).isEqualTo("database_pool_exhaustion");
        assertThat(r.status()).isEqualTo("triggered");
        assertThat(r.description()).isEqualTo("desc");
        assertThat(r.triggeredAt()).isNotNull();
    }

    @Test
    void reset_setsResetStatus() {
        var r = IncidentResponse.reset("INC-004", "config_regression");

        assertThat(r.incidentId()).isEqualTo("INC-004");
        assertThat(r.scenario()).isEqualTo("config_regression");
        assertThat(r.status()).isEqualTo("reset");
        assertThat(r.triggeredAt()).isNotNull();
    }
}
