package com.rke.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import com.rke.backend.simulation.scenario.BackendExceptionScenario;
import com.rke.backend.simulation.scenario.CascadeFailureScenario;
import com.rke.backend.simulation.scenario.ConfigRegressionScenario;
import com.rke.backend.simulation.scenario.DbPoolExhaustionScenario;
import com.rke.backend.simulation.scenario.HistoricalIncidentScenario;
import com.rke.backend.simulation.scenario.SlowQueryScenario;

import java.util.Arrays;

/**
 * Verifies that the simulation controller and its security configuration are
 * correctly guarded by the {@code dev} Spring profile.
 *
 * <p>Uses structural (reflection) checks rather than booting a Spring context
 * so the test remains fast and requires no infrastructure (no database, no
 * security).
 *
 * <p>This covers acceptance criterion #7:
 * "No simulation endpoint is exposed in production mode."
 */
class ProfileGuardTest {

    // -------------------------------------------------------------------------
    // IncidentSimulationController must be @Profile("dev")
    // -------------------------------------------------------------------------

    @Test
    void incidentSimulationController_isAnnotatedWithDevProfile() {
        Profile profile = IncidentSimulationController.class.getAnnotation(Profile.class);

        assertThat(profile)
                .as("IncidentSimulationController must carry @Profile")
                .isNotNull();

        assertThat(Arrays.asList(profile.value()))
                .as("IncidentSimulationController profile must include 'dev'")
                .contains("dev");
    }

    @Test
    void incidentSimulationController_doesNotIncludeProdProfile() {
        Profile profile = IncidentSimulationController.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();

        assertThat(Arrays.asList(profile.value()))
                .as("IncidentSimulationController must NOT include 'prod' profile")
                .doesNotContain("prod");
    }

    // -------------------------------------------------------------------------
    // SimulationSecurityConfig must be @Profile("dev")
    // -------------------------------------------------------------------------

    @Test
    void simulationSecurityConfig_isAnnotatedWithDevProfile() {
        Profile profile = SimulationSecurityConfig.class.getAnnotation(Profile.class);

        assertThat(profile)
                .as("SimulationSecurityConfig must carry @Profile")
                .isNotNull();

        assertThat(Arrays.asList(profile.value()))
                .as("SimulationSecurityConfig profile must include 'dev'")
                .contains("dev");
    }

    @Test
    void simulationSecurityConfig_doesNotIncludeProdProfile() {
        Profile profile = SimulationSecurityConfig.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();

        assertThat(Arrays.asList(profile.value()))
                .as("SimulationSecurityConfig must NOT include 'prod' profile")
                .doesNotContain("prod");
    }

    // -------------------------------------------------------------------------
    // Scenario beans must NOT carry any profile restriction — they exist in all
    // profiles but are no-ops when the simulation flag is disabled. Only the
    // controller and security config that EXPOSE the endpoints are profile-gated.
    // -------------------------------------------------------------------------

    @Test
    void scenarioBeans_areNotProfileRestricted() {
        // Scenario beans are @Component (no @Profile), intentionally —
        // they're harmless unless SimulationState flags them active.
        assertThat(DbPoolExhaustionScenario.class.getAnnotation(Profile.class))
                .as("DbPoolExhaustionScenario should not be @Profile-restricted")
                .isNull();

        assertThat(SlowQueryScenario.class.getAnnotation(Profile.class)).isNull();
        assertThat(BackendExceptionScenario.class.getAnnotation(Profile.class)).isNull();
        assertThat(ConfigRegressionScenario.class.getAnnotation(Profile.class)).isNull();
        assertThat(CascadeFailureScenario.class.getAnnotation(Profile.class)).isNull();
        assertThat(HistoricalIncidentScenario.class.getAnnotation(Profile.class)).isNull();
    }

    // -------------------------------------------------------------------------
    // SimulationState (the registry) has no Spring wiring at all
    // -------------------------------------------------------------------------

    @Test
    void simulationState_isAnEnum_notASpringBean() {
        // SimulationState is a plain enum — verify it has no Spring stereotype
        assertThat(SimulationState.class.isEnum()).isTrue();
        assertThat(SimulationState.class.getAnnotation(org.springframework.stereotype.Component.class))
                .isNull();
    }
}
