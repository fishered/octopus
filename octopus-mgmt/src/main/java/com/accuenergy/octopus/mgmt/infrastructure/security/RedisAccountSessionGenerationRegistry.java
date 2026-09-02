package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisAccountSessionGenerationRegistry implements AccountSessionGenerationRegistry {
    private static final DefaultRedisScript<Long> ENSURE_BASELINE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            local baseline = tonumber(ARGV[1])
            if (not current) or (tonumber(current) < baseline) then
                redis.call('SET', KEYS[1], ARGV[1])
                return baseline
            end
            return tonumber(current)
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String environment;

    public RedisAccountSessionGenerationRegistry(StringRedisTemplate redis,
            @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.environment = environment;
    }

    @Override
    public long current(UUID accountId, long persistentBaseline) {
        Long value = redis.execute(ENSURE_BASELINE, List.of(key(accountId)),
                Long.toString(persistentBaseline));
        if (value == null) throw new IllegalStateException("Account session generation is unavailable");
        return value;
    }

    @Override
    public long revokeAll(UUID accountId, long persistentBaseline) {
        current(accountId, persistentBaseline);
        Long next = redis.opsForValue().increment(key(accountId));
        if (next == null) throw new IllegalStateException("Unable to advance account session generation");
        return next;
    }

    private String key(UUID accountId) {
        return "octopus:" + environment + ":auth:account:" + accountId + ":generation";
    }
}
