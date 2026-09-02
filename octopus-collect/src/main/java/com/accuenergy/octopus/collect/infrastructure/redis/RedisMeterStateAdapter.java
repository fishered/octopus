package com.accuenergy.octopus.collect.infrastructure.redis;

import com.accuenergy.octopus.collect.application.port.MeterStatePort;
import com.accuenergy.octopus.collect.domain.meter.CumulativeReading;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisMeterStateAdapter implements MeterStatePort {
    private static final DefaultRedisScript<Long> SAVE_STATE = new DefaultRedisScript<>("""
            local currentBoot = redis.call('HGET', KEYS[1], 'boot_id')
            if currentBoot and currentBoot ~= ARGV[1] then
              redis.call('SADD', KEYS[3], currentBoot)
            end
            redis.call('HSET', KEYS[1],
              'boot_id', ARGV[1], 'last_event_id', ARGV[2], 'sequence', ARGV[3],
              'occurred_at', ARGV[4], 'value', ARGV[5])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[6])
            redis.call('EXPIRE', KEYS[3], ARGV[6])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String environment;
    private final Duration deduplicationTtl;

    public RedisMeterStateAdapter(StringRedisTemplate redis,
                                  @Value("${octopus.environment:local}") String environment,
                                  @Value("${octopus.collect.deduplication-ttl:PT168H}") Duration deduplicationTtl) {
        this.redis = redis;
        this.environment = environment;
        this.deduplicationTtl = deduplicationTtl;
    }

    @Override
    public Optional<CumulativeReading> previous(UUID tenantId, UUID meterId) {
        Map<Object, Object> values = redis.opsForHash().entries(stateKey(tenantId, meterId));
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(new CumulativeReading(
                required(values, "boot_id"),
                UUID.fromString(required(values, "last_event_id")),
                Long.parseLong(required(values, "sequence")),
                Instant.parse(required(values, "occurred_at")),
                new BigDecimal(required(values, "value"))));
    }

    @Override
    public void save(UUID tenantId, UUID meterId, CumulativeReading reading) {
        Long result = redis.execute(SAVE_STATE, List.of(stateKey(tenantId, meterId),
                        eventKey(tenantId, meterId, reading.eventId()), retiredBootsKey(tenantId, meterId)),
                reading.bootId(), reading.eventId().toString(), Long.toString(reading.sequence()),
                reading.occurredAt().toString(), reading.value().toPlainString(),
                Long.toString(Math.max(1, deduplicationTtl.toSeconds())));
        if (result == null || result != 1) throw new IllegalStateException("Unable to persist meter state");
    }

    @Override
    public void markProcessed(UUID tenantId, UUID meterId, UUID eventId) {
        redis.opsForValue().set(eventKey(tenantId, meterId, eventId), "1", deduplicationTtl);
    }

    @Override
    public boolean wasProcessed(UUID tenantId, UUID meterId, UUID eventId) {
        return Boolean.TRUE.equals(redis.hasKey(eventKey(tenantId, meterId, eventId)));
    }

    @Override
    public boolean isRetiredBoot(UUID tenantId, UUID meterId, String bootId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(retiredBootsKey(tenantId, meterId), bootId));
    }

    private String stateKey(UUID tenantId, UUID meterId) {
        return prefix(tenantId, meterId) + ":state";
    }

    private String eventKey(UUID tenantId, UUID meterId, UUID eventId) {
        return prefix(tenantId, meterId) + ":event:" + eventId;
    }

    private String retiredBootsKey(UUID tenantId, UUID meterId) {
        return prefix(tenantId, meterId) + ":retired-boots";
    }

    private String prefix(UUID tenantId, UUID meterId) {
        return "octopus:" + environment + ":{" + tenantId + ":" + meterId + "}:collect:meter:" + meterId;
    }

    private static String required(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) throw new IllegalStateException("Corrupt meter state: missing " + field);
        return value.toString();
    }
}
