package com.accuenergy.octopus.control.infrastructure.presence;

import com.accuenergy.octopus.control.application.port.DevicePresenceLeaseWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisDevicePresenceLeaseWriter implements DevicePresenceLeaseWriter {
    private static final DefaultRedisScript<Long> OBSERVE = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            local observed = tonumber(ARGV[1])
            if previous and tonumber(previous) > observed then return 0 end
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('SET', KEYS[2], ARGV[1] .. ':' .. ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisDevicePresenceLeaseWriter(StringRedisTemplate redis,
            @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.prefix = "octopus:" + environment + ":device-presence:";
    }

    @Override
    public void observe(UUID tenantId, UUID deviceId, Instant observedAt, Duration leaseDuration) {
        long observedMillis = observedAt.toEpochMilli();
        long leaseMillis = leaseDuration.toMillis();
        Long result = redis.execute(OBSERVE, List.of(lastKey(tenantId, deviceId), leaseKey(tenantId, deviceId)),
                Long.toString(observedMillis), Long.toString(observedMillis + leaseMillis),
                Long.toString(leaseMillis));
        if (result == null) throw new IllegalStateException("Redis presence update returned no result");
    }

    private String lastKey(UUID tenantId, UUID deviceId) {
        return prefix + tenantId + ":" + deviceId + ":last";
    }

    private String leaseKey(UUID tenantId, UUID deviceId) {
        return prefix + tenantId + ":" + deviceId + ":lease";
    }
}
