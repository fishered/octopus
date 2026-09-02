package com.accuenergy.octopus.iot.spi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Selects a device-bound plugin, falling back to the configured platform plugin. */
public final class DeviceTransportRouter {
    private final DeviceTransportPluginRegistry registry;
    private final DeviceBindingResolver bindings;
    private final String defaultPluginId;

    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings,
            String defaultPluginId) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        if (defaultPluginId == null || defaultPluginId.isBlank()) throw new IllegalArgumentException("defaultPluginId is required");
        this.defaultPluginId = defaultPluginId.strip();
    }

    public CompletionStage<PublishReceipt> publish(OutboundMessage message) {
        var binding = bindings.find(message.tenantId(), message.deviceId());
        if (binding.isPresent() && !binding.get().tenantId().equals(message.tenantId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Device binding tenant does not match message"));
        }
        String pluginId = binding.map(DeviceBinding::pluginId).orElse(defaultPluginId);
        DeviceTransportPlugin plugin;
        try {
            plugin = registry.require(pluginId);
        } catch (RuntimeException missing) {
            return CompletableFuture.failedFuture(missing);
        }
        if (!plugin.descriptor().messageKinds().contains(message.kind())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "IoT plugin does not support message kind " + message.kind() + ": " + pluginId));
        }
        return plugin.publish(message);
    }
}
