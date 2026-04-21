package com.hospital.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;

abstract class AbstractIntegrationTest {

    @BeforeAll
    static void checkDockerAvailable() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Skipping integration test: Docker is not available"
        );
    }
}
