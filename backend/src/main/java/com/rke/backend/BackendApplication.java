package com.rke.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.rke.backend.simulation.scenario.ConfigRegressionScenario;

@SpringBootApplication
@EnableConfigurationProperties(ConfigRegressionScenario.class)
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
