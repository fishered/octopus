package com.accuenergy.octopus.control.application.port;

import java.time.Instant;
import java.util.UUID;

public interface DeviceShadowPayloadDecoder {
    DecodedShadow decode(byte[] payload);

    record DecodedShadow(int schemaVersion, UUID reportId, UUID tenantId, UUID deviceId,
            long shadowVersion, String stateJson, Instant reportedAt, Long appliedDesiredVersion) {
        public DecodedShadow(int schemaVersion, UUID reportId, UUID tenantId, UUID deviceId,
                long shadowVersion, String stateJson, Instant reportedAt) {
            this(schemaVersion, reportId, tenantId, deviceId, shadowVersion, stateJson, reportedAt, null);
        }
    }
}
