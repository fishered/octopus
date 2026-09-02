package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.RefreshTokenStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisRefreshTokenStore implements RefreshTokenStore {
    private final StringRedisTemplate redis;
    private final HmacRefreshTokenHasher hasher;
    private final String environment;

    public RedisRefreshTokenStore(StringRedisTemplate redis, HmacRefreshTokenHasher hasher,
                                  @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.hasher = hasher;
        this.environment = environment;
    }

    @Override
    public void replace(UUID sessionId, long refreshGeneration, String rawToken) {
        redis.opsForHash().putAll(key(sessionId), Map.of(
                "refresh_token_hash", hasher.hash(rawToken),
                "refresh_token_generation", Long.toString(refreshGeneration)));
    }

    @Override
    public boolean matches(UUID sessionId, long refreshGeneration, String presentedToken) {
        Object generation = redis.opsForHash().get(key(sessionId), "refresh_token_generation");
        Object hash = redis.opsForHash().get(key(sessionId), "refresh_token_hash");
        if (generation == null || hash == null || presentedToken == null) return false;
        return Long.toString(refreshGeneration).equals(generation.toString())
                && hasher.matches(presentedToken, hash.toString());
    }

    private String key(UUID sessionId) {
        return "octopus:" + environment + ":auth:session:" + sessionId;
    }
}

