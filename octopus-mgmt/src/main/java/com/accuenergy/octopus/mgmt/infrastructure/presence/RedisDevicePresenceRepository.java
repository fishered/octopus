package com.accuenergy.octopus.mgmt.infrastructure.presence;

import com.accuenergy.octopus.api.control.DevicePresenceSnapshot;
import com.accuenergy.octopus.mgmt.application.monitoring.DevicePresenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisDevicePresenceRepository implements DevicePresenceRepository {
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final String prefix;

    public RedisDevicePresenceRepository(StringRedisTemplate redis, Clock clock,
            @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.clock = clock;
        this.prefix = "octopus:" + environment + ":device-presence:";
    }

    @Override
    public DevicePresenceSnapshot find(UUID tenantId, UUID deviceId) {
        String lastValue = redis.opsForValue().get(lastKey(tenantId, deviceId));
        String leaseValue = redis.opsForValue().get(leaseKey(tenantId, deviceId));
        return interpret(tenantId, deviceId, lastValue, leaseValue, clock.instant());
    }

    static DevicePresenceSnapshot interpret(UUID tenantId, UUID deviceId, String lastValue,
            String leaseValue, Instant now) {
        if (lastValue == null) {
            return new DevicePresenceSnapshot(tenantId, deviceId, DevicePresenceSnapshot.Status.NEVER_SEEN,
                    null, null);
        }
        Instant lastSeen = parseInstant(lastValue, "last-seen");
        if (leaseValue != null) {
            String[] parts = leaseValue.split(":", -1);
            if (parts.length != 2) throw new IllegalStateException("Malformed device presence lease");
            Instant leaseObserved = parseInstant(parts[0], "lease observation");
            Instant leaseExpires = parseInstant(parts[1], "lease expiry");
            if (leaseObserved.equals(lastSeen) && leaseExpires.isAfter(now)) {
                return new DevicePresenceSnapshot(tenantId, deviceId, DevicePresenceSnapshot.Status.ONLINE,
                        lastSeen, leaseExpires);
            }
        }
        return new DevicePresenceSnapshot(tenantId, deviceId, DevicePresenceSnapshot.Status.OFFLINE,
                lastSeen, null);
    }

    private static Instant parseInstant(String epochMillis, String field) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(epochMillis));
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Malformed device presence " + field, malformed);
        }
    }

    private String lastKey(UUID tenantId, UUID deviceId) { return prefix + tenantId + ":" + deviceId + ":last"; }
    private String leaseKey(UUID tenantId, UUID deviceId) { return prefix + tenantId + ":" + deviceId + ":lease"; }
}
