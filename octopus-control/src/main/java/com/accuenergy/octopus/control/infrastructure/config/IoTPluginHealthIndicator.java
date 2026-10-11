package com.accuenergy.octopus.control.infrastructure.config;

import com.accuenergy.octopus.iot.spi.DeviceTransportPlugin;
import com.accuenergy.octopus.iot.spi.DeviceTransportPluginRegistry;
import com.accuenergy.octopus.iot.spi.PluginHealth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Exposes installed plugin identity and sanitized health through the control health endpoint. */
@Component("iotPlugins")
public final class IoTPluginHealthIndicator implements HealthIndicator {
    private final DeviceTransportPluginRegistry plugins;

    public IoTPluginHealthIndicator(DeviceTransportPluginRegistry plugins) {
        this.plugins = plugins;
    }

    @Override
    public Health health() {
        boolean down = false;
        Map<String, Object> details = new LinkedHashMap<>();
        for (DeviceTransportPlugin plugin : plugins.all().stream()
                .sorted(java.util.Comparator.comparing(value -> value.descriptor().pluginId())).toList()) {
            try {
                PluginHealth health = plugin.health();
                down |= health.status() == PluginHealth.Status.DOWN;
                details.put(plugin.descriptor().pluginId(), Map.of(
                        "version", plugin.descriptor().version(),
                        "spiVersion", plugin.descriptor().spiVersion(),
                        "messageKinds", plugin.descriptor().messageKinds().stream()
                                .map(Enum::name).sorted().toList(),
                        "codecIds", plugin.descriptor().codecIds().stream().sorted().toList(),
                        "status", health.status().name(),
                        "checkedAt", health.checkedAt().toString()));
            } catch (RuntimeException failure) {
                down = true;
                details.put(plugin.descriptor().pluginId(), Map.of(
                        "version", plugin.descriptor().version(),
                        "status", PluginHealth.Status.DOWN.name(),
                        "detail", "health check failed"));
            }
        }
        return (down ? Health.down() : Health.up())
                .withDetail("installed", details.size())
                .withDetail("plugins", details)
                .build();
    }
}
