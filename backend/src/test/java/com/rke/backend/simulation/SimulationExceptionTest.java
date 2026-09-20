package com.rke.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SimulationException} construction and message formatting.
 */
class SimulationExceptionTest {

    @Test
    void constructor_embedsIncidentIdInMessage() {
        var ex = new SimulationException("INC-001", "pool exhausted");
        assertThat(ex.getMessage()).contains("INC-001").contains("pool exhausted");
        assertThat(ex.getIncidentId()).isEqualTo("INC-001");
    }

    @Test
    void constructor_withCause_preservesCauseChain() {
        var cause = new RuntimeException("root cause");
        var ex = new SimulationException("INC-003", "downstream error", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).contains("INC-003").contains("downstream error");
    }

    @Test
    void extendsRuntimeException_soItPropagatesUnforced() {
        assertThat(new SimulationException("INC-002", "test"))
                .isInstanceOf(RuntimeException.class);
    }
}
