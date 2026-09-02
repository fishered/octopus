package com.accuenergy.octopus.mgmt.infrastructure.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.accuenergy.octopus.api.control.DevicePresenceSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisDevicePresenceRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID tenantId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();

    @Test
    void interpretsNeverSeenOnlineAndExpiredLease() {
        assertEquals(DevicePresenceSnapshot.Status.NEVER_SEEN,
                RedisDevicePresenceRepository.interpret(tenantId, deviceId, null, null, NOW).status());

        String observed = Long.toString(NOW.toEpochMilli());
        String activeLease = observed + ":" + NOW.plusSeconds(120).toEpochMilli();
        assertEquals(DevicePresenceSnapshot.Status.ONLINE,
                RedisDevicePresenceRepository.interpret(tenantId, deviceId, observed, activeLease, NOW).status());

        String expiredLease = observed + ":" + NOW.minusSeconds(1).toEpochMilli();
        assertEquals(DevicePresenceSnapshot.Status.OFFLINE,
                RedisDevicePresenceRepository.interpret(tenantId, deviceId, observed, expiredLease, NOW).status());
    }

    @Test
    void staleLeaseCannotMakeNewerObservationOnline() {
        String latest = Long.toString(NOW.toEpochMilli());
        String staleLease = NOW.minusSeconds(1).toEpochMilli() + ":" + NOW.plusSeconds(120).toEpochMilli();
        assertEquals(DevicePresenceSnapshot.Status.OFFLINE,
                RedisDevicePresenceRepository.interpret(tenantId, deviceId, latest, staleLease, NOW).status());
    }
}
