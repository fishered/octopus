package com.accuenergy.octopus.iot.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Immutable plugin registry assembled at startup; duplicate IDs fail fast. */
public final class DeviceTransportPluginRegistry {
    private final Map<String, DeviceTransportPlugin> plugins;

    public DeviceTransportPluginRegistry(List<? extends DeviceTransportPlugin> plugins) {
        Objects.requireNonNull(plugins, "plugins");
        this.plugins = plugins.stream().collect(Collectors.toUnmodifiableMap(
                plugin -> Objects.requireNonNull(plugin, "plugin").descriptor().pluginId(),
                Function.identity(),
                (left, right) -> { throw new IllegalArgumentException("Duplicate IoT plugin: " + left.descriptor().pluginId()); }));
    }

    public Optional<DeviceTransportPlugin> find(String pluginId) { return Optional.ofNullable(plugins.get(pluginId)); }

    public DeviceTransportPlugin require(String pluginId) {
        return find(pluginId).orElseThrow(() -> new IllegalArgumentException("IoT plugin is not installed: " + pluginId));
    }

    public List<DeviceTransportPlugin> all() { return List.copyOf(plugins.values()); }
}
