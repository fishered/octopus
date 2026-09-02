package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MybatisDeviceDesiredShadowRepositoryTest {
    private final MybatisDeviceDesiredShadowRepository repository =
            new MybatisDeviceDesiredShadowRepository(null, null, new ObjectMapper(), "commands");

    @Test
    void reportedSupersetContainsDesiredState() {
        assertTrue(repository.reportedContainsDesired(
                "{\"relay\":true,\"voltage\":230.1}",
                "{\"relay\":true}"));
    }

    @Test
    void reportedStateWithChangedDesiredValueDoesNotMatch() {
        assertFalse(repository.reportedContainsDesired(
                "{\"relay\":false}",
                "{\"relay\":true}"));
    }

    @Test
    void reportedStateMissingDesiredValueDoesNotMatch() {
        assertFalse(repository.reportedContainsDesired(
                "{\"voltage\":230.1}",
                "{\"relay\":true}"));
    }
}
