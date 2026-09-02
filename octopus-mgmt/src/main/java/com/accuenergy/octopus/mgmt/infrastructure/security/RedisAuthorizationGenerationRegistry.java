package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisAuthorizationGenerationRegistry implements AuthorizationGenerationRegistry {
    private static final DefaultRedisScript<Long> MAX_BASELINE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if (not current) or tonumber(current) < tonumber(ARGV[1]) then
              redis.call('SET', KEYS[1], ARGV[1])
              return tonumber(ARGV[1])
            end
            return tonumber(current)
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String environment;

    public RedisAuthorizationGenerationRegistry(StringRedisTemplate redis,
            @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.environment = environment;
    }

    @Override
    public long current(UUID accountId, Optional<UUID> tenantId, long persistentBaseline) {
        Long value = redis.execute(MAX_BASELINE, List.of(key(accountId, tenantId)),
                Long.toString(persistentBaseline));
        if (value == null) throw new IllegalStateException("Authorization generation is unavailable");
        return value;
    }

    @Override
    public long revoke(UUID accountId, Optional<UUID> tenantId, long persistentBaseline) {
        current(accountId, tenantId, persistentBaseline);
        Long next = redis.opsForValue().increment(key(accountId, tenantId));
        if (next == null) throw new IllegalStateException("Unable to advance authorization generation");
        return next;
    }

    private String key(UUID accountId, Optional<UUID> tenantId) {
        return "octopus:" + environment + ":auth:authorization:"
                + tenantId.map(UUID::toString).orElse("platform") + ":account:" + accountId + ":generation";
    }
}
