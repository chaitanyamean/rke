package com.rke.backend.simulation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rke.backend.simulation.SimulationException;
import com.rke.backend.simulation.SimulationState;

/**
 * Tests for {@link SlowQueryScenario}.
 *
 * <p>The DataSource is mocked so pg_sleep is not actually executed — the test
 * verifies the control flow and exception propagation paths only.
 */
class SlowQueryScenarioTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    private SlowQueryScenario scenario;

    @BeforeEach
    void setUp() throws Exception {
        dataSource  = mock(DataSource.class);
        connection  = mock(Connection.class);
        statement   = mock(PreparedStatement.class);
        resultSet   = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false); // empty result is fine

        scenario = new SlowQueryScenario(dataSource);
    }

    @AfterEach
    void cleanup() {
        SimulationState.resetAll();
    }

    // -------------------------------------------------------------------------
    // Scenario disabled
    // -------------------------------------------------------------------------

    @Test
    void disabled_doesNotTouchDataSource() throws Exception {
        assertThatNoException().isThrownBy(() -> scenario.run("INC-002"));
        verify(dataSource, never()).getConnection();
    }

    // -------------------------------------------------------------------------
    // Scenario enabled
    // -------------------------------------------------------------------------

    @Test
    void enabled_executesPgSleep() throws Exception {
        SimulationState.enable(SimulationState.SLOW_QUERY);

        // When SIMULATE_TIMEOUT=true, run() throws after executing the query.
        assertThatThrownBy(() -> scenario.run("INC-002"))
                .isInstanceOf(SimulationException.class);

        verify(dataSource).getConnection();
        verify(connection).prepareStatement("SELECT pg_sleep(?)");
        verify(statement).setInt(1, SlowQueryScenario.SLEEP_SECONDS);
        verify(statement).executeQuery();
    }

    @Test
    void enabled_throwsSimulationException_withTimeoutMessage() {
        SimulationState.enable(SimulationState.SLOW_QUERY);

        assertThatThrownBy(() -> scenario.run("INC-002"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("INC-002")
                .hasMessageContaining("timeout");
    }

    @Test
    void enabled_incidentIdPropagated() {
        SimulationState.enable(SimulationState.SLOW_QUERY);

        assertThatThrownBy(() -> scenario.run("INC-002"))
                .isInstanceOf(SimulationException.class)
                .satisfies(ex -> assertThat(((SimulationException) ex).getIncidentId())
                        .isEqualTo("INC-002"));
    }

    @Test
    void enabled_dataSourceException_isWrappedInSimulationException() throws Exception {
        SimulationState.enable(SimulationState.SLOW_QUERY);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("connection refused"));

        assertThatThrownBy(() -> scenario.run("INC-002"))
                .isInstanceOf(SimulationException.class)
                .hasMessageContaining("unexpected error");
    }
}
