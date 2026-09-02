package com.accuenergy.octopus.control.infrastructure.mqtt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PahoCommandResultHandlerTest {
    @Test
    void acceptsOnlyCanonicalCommandResultTopics() {
        String tenantId = UUID.randomUUID().toString();
        String deviceId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();

        assertTrue(PahoCommandResultHandler.isCommandResultTopic("octopus/" + tenantId + "/devices/"
                + deviceId + "/command-results/" + commandId));
        assertFalse(PahoCommandResultHandler.isCommandResultTopic("octopus/" + tenantId + "/devices/"
                + deviceId + "/commands/" + commandId));
        assertTrue(PahoCommandResultHandler.isCommandResultTopic("octopus/not-a-uuid/devices/"
                + deviceId + "/command-results/" + commandId));
        assertFalse(PahoCommandResultHandler.isCommandResultTopic(null));
    }
}
