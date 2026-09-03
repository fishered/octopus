package com.accuenergy.octopus.control.infrastructure.redis;

import com.accuenergy.octopus.iot.spi.DeviceBinding;
import com.accuenergy.octopus.iot.spi.DeviceBindingResolver;
import com.accuenergy.octopus.api.control.DeviceConnectorBindingChanged;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public final class RedisDeviceBindingResolver implements DeviceBindingResolver {
    private static final DefaultRedisScript<Long> APPLY_IF_NEWER = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'version')
            if current and tonumber(current) >= tonumber(ARGV[1]) then return 0 end
            redis.call('HSET', KEYS[1], 'version', ARGV[1], 'payload', ARGV[2], 'status', ARGV[3])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis; private final ObjectMapper json; private final String environment;
    public RedisDeviceBindingResolver(StringRedisTemplate redis, ObjectMapper json,
            @Value("$"+"{octopus.environment:local}") String environment) { this.redis=redis; this.json=json; this.environment=environment; }
    @Override public Optional<DeviceBinding> find(UUID tenantId, UUID deviceId) {
        Map<Object,Object> values=redis.opsForHash().entries(key(tenantId,deviceId)); if(values.isEmpty()) return Optional.empty();
        if ("DISABLED".equals(String.valueOf(values.get("status")))) return Optional.empty();
        Object payload=values.get("payload"); if(payload==null) return Optional.empty();
        try { return Optional.of(json.readValue(payload.toString(), DeviceBinding.class)); }
        catch(Exception e) { throw new IllegalStateException("Invalid cached device binding",e); }
    }
    public void apply(DeviceConnectorBindingChanged event) {
        try {
            String payload=json.writeValueAsString(new DeviceBinding(event.tenantId(),event.deviceId(),event.pluginId(),event.codecId(),event.bindingVersion()));
            redis.execute(APPLY_IF_NEWER,List.of(key(event.tenantId(),event.deviceId())),Long.toString(event.bindingVersion()),payload,event.status());
        } catch(Exception e) { throw new IllegalStateException("Unable to cache device binding",e); }
    }
    private String key(UUID tenantId,UUID deviceId){return "octopus:"+environment+":binding:"+tenantId+":"+deviceId;}
}
