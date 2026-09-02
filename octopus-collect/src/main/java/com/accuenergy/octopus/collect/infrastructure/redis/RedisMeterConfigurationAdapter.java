package com.accuenergy.octopus.collect.infrastructure.redis;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.collect.application.port.MeterConfigurationPort;
import com.accuenergy.octopus.collect.application.port.MeterConfigurationUpdatePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisMeterConfigurationAdapter implements MeterConfigurationPort, MeterConfigurationUpdatePort {
    private static final DefaultRedisScript<Long> APPLY_IF_NEWER = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'version')
            if current and tonumber(current) > tonumber(ARGV[1]) then
              return 0
            end
            redis.call('HSET', KEYS[1], 'version', ARGV[1], 'payload', ARGV[2])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String environment;

    public RedisMeterConfigurationAdapter(StringRedisTemplate redis, ObjectMapper objectMapper,
                                          @Value("${octopus.environment:local}") String environment) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    public Optional<MeterConfiguration> find(UUID tenantId, UUID meterId, long modelVersion) {
        Object stored = redis.opsForHash().get(key(tenantId, meterId, modelVersion), "payload");
        String value = stored == null ? null : stored.toString();
        if (value == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(value, MeterConfiguration.class));
        } catch (Exception malformed) {
            throw new IllegalStateException("Invalid cached meter configuration", malformed);
        }
    }

    @Override
    public void apply(MeterConfigurationChanged event) {
        MeterConfiguration configuration = new MeterConfiguration(event.parameterId(),
                MeterConfiguration.ValueSemantics.valueOf(event.semantics().name()), event.sourceUnitCode(),
                event.canonicalUnitCode(), event.scale(), event.offset(),
                Optional.ofNullable(event.rolloverModulus()), event.configurationVersion(), event.algorithmVersion());
        try {
            String payload = objectMapper.writeValueAsString(configuration);
            redis.execute(APPLY_IF_NEWER, List.of(key(event.tenantId(), event.meterId(), event.modelVersion())),
                    Long.toString(event.configurationVersion()), payload);
        } catch (Exception serializationFailure) {
            throw new IllegalStateException("Unable to cache meter configuration", serializationFailure);
        }
    }

    private String key(UUID tenantId, UUID meterId, long modelVersion) {
        return "octopus:" + environment + ":" + tenantId + ":catalog:meter:" + meterId + ":v" + modelVersion;
    }
}
