package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.MutableSessionRegistry;
import com.accuenergy.octopus.mgmt.application.identity.SessionSnapshot;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisSessionRegistry implements MutableSessionRegistry {
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local status = redis.call('HGET', KEYS[1], 'status')
            if not status then return -1 end
            if status ~= 'ACTIVE' then return -2 end
            local generation = tonumber(redis.call('HGET', KEYS[1], 'refresh_generation'))
            if generation ~= tonumber(ARGV[1]) then
              redis.call('HSET', KEYS[1], 'status', 'REVOKED', 'revoke_reason', 'refresh_token_reuse')
              return -3
            end
            local idle = tonumber(redis.call('HGET', KEYS[1], 'idle_expires_epoch_ms'))
            local absolute = tonumber(redis.call('HGET', KEYS[1], 'absolute_expires_epoch_ms'))
            if idle <= tonumber(ARGV[2]) or absolute <= tonumber(ARGV[2]) then
              redis.call('HSET', KEYS[1], 'status', 'EXPIRED')
              return -4
            end
            local nextIdle = tonumber(ARGV[3])
            if nextIdle > absolute then nextIdle = absolute end
            redis.call('HSET', KEYS[1], 'refresh_generation', generation + 1,
              'authorization_generation', ARGV[5],
              'idle_expires_epoch_ms', nextIdle, 'idle_expires_at', ARGV[4])
            return generation + 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String environment;
    private final com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry accountGenerations;

    public RedisSessionRegistry(StringRedisTemplate redis,
                                com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry accountGenerations,
                                @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.accountGenerations = accountGenerations;
        this.environment = environment;
    }

    @Override
    public Optional<SessionSnapshot> find(UUID sessionId) {
        Map<Object, Object> values = redis.opsForHash().entries(key(sessionId));
        if (values.isEmpty()) return Optional.empty();
        try {
            SessionSnapshot snapshot = new SessionSnapshot(
                    sessionId,
                    UUID.fromString(required(values, "account_id")),
                    optional(values, "tenant_id").map(UUID::fromString),
                    Long.parseLong(required(values, "refresh_generation")),
                    Long.parseLong(required(values, "account_generation")),
                    Long.parseLong(required(values, "authorization_generation")),
                    SessionSnapshot.Status.valueOf(required(values, "status")),
                    Instant.parse(required(values, "idle_expires_at")),
                    Instant.parse(required(values, "absolute_expires_at")));
            return accountGenerations.current(snapshot.accountId(), snapshot.accountGeneration()) == snapshot.accountGeneration()
                    ? Optional.of(snapshot) : Optional.empty();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    @Override
    public void revoke(UUID sessionId, String reason) {
        redis.opsForHash().put(key(sessionId), "status", SessionSnapshot.Status.REVOKED.name());
        redis.opsForHash().put(key(sessionId), "revoke_reason", reason == null ? "forced" : reason);
    }

    @Override
    public SessionSnapshot create(UUID accountId, Optional<UUID> tenantId, long accountSessionGeneration,
                                  long authorizationGeneration, Instant now,
                                  Duration idleTimeout, Duration absoluteTimeout) {
        UUID sessionId = UUID.randomUUID();
        Instant idleExpiresAt = now.plus(idleTimeout);
        Instant absoluteExpiresAt = now.plus(absoluteTimeout);
        if (idleExpiresAt.isAfter(absoluteExpiresAt)) idleExpiresAt = absoluteExpiresAt;
        Map<String, String> values = new java.util.HashMap<>();
        values.put("account_id", accountId.toString());
        tenantId.ifPresent(value -> values.put("tenant_id", value.toString()));
        values.put("refresh_generation", "0");
        values.put("account_generation", Long.toString(accountSessionGeneration));
        values.put("authorization_generation", Long.toString(authorizationGeneration));
        values.put("status", SessionSnapshot.Status.ACTIVE.name());
        values.put("idle_expires_at", idleExpiresAt.toString());
        values.put("absolute_expires_at", absoluteExpiresAt.toString());
        values.put("idle_expires_epoch_ms", Long.toString(idleExpiresAt.toEpochMilli()));
        values.put("absolute_expires_epoch_ms", Long.toString(absoluteExpiresAt.toEpochMilli()));
        redis.opsForHash().putAll(key(sessionId), values);
        redis.expireAt(key(sessionId), absoluteExpiresAt);
        return new SessionSnapshot(sessionId, accountId, tenantId, 0, accountSessionGeneration,
                authorizationGeneration,
                SessionSnapshot.Status.ACTIVE, idleExpiresAt, absoluteExpiresAt);
    }

    @Override
    public SessionSnapshot rotate(UUID sessionId, long expectedGeneration, long authorizationGeneration,
                                  Instant now, Duration idleTimeout) {
        Instant candidateIdle = now.plus(idleTimeout);
        Long result = redis.execute(ROTATE_SCRIPT, java.util.List.of(key(sessionId)),
                Long.toString(expectedGeneration), Long.toString(now.toEpochMilli()),
                Long.toString(candidateIdle.toEpochMilli()), candidateIdle.toString(),
                Long.toString(authorizationGeneration));
        if (result == null || result < 0) {
            throw new org.springframework.security.authentication.BadCredentialsException("Session refresh rejected");
        }
        return find(sessionId).orElseThrow(() ->
                new org.springframework.security.authentication.BadCredentialsException("Session refresh rejected"));
    }

    private String key(UUID sessionId) {
        return "octopus:" + environment + ":auth:session:" + sessionId;
    }

    private static String required(Map<Object, Object> values, String name) {
        Object value = values.get(name);
        if (value == null) throw new IllegalArgumentException("Missing session field " + name);
        return value.toString();
    }

    private static Optional<String> optional(Map<Object, Object> values, String name) {
        Object value = values.get(name);
        return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString());
    }
}
