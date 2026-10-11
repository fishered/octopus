package com.accuenergy.octopus.iot.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Owns the lifecycle of every installed transport plugin.
 *
 * <p>Plugins start in deterministic ID order. A partial startup is rolled back, and shutdown
 * happens in reverse order so provider dependencies can unwind safely.</p>
 */
public final class DeviceTransportPluginRuntime implements AutoCloseable {
    private final DeviceTransportPluginRegistry registry;
    private final InboundMessageHandler inboundHandler;
    private final List<DeviceTransportPlugin> started = new ArrayList<>();
    private boolean running;

    public DeviceTransportPluginRuntime(DeviceTransportPluginRegistry registry,
            InboundMessageHandler inboundHandler) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.inboundHandler = Objects.requireNonNull(inboundHandler, "inboundHandler");
    }

    public synchronized void start() {
        if (running) return;
        List<DeviceTransportPlugin> plugins = registry.all().stream()
                .sorted(java.util.Comparator.comparing(plugin -> plugin.descriptor().pluginId()))
                .toList();
        try {
            for (DeviceTransportPlugin plugin : plugins) {
                started.add(plugin);
                plugin.start(message -> dispatch(plugin, message));
            }
            running = true;
        } catch (RuntimeException startupFailure) {
            RuntimeException rollbackFailure = stopStarted();
            if (rollbackFailure != null) startupFailure.addSuppressed(rollbackFailure);
            throw new IllegalStateException("Unable to start IoT plugin runtime", startupFailure);
        }
    }

    private java.util.concurrent.CompletionStage<Void> dispatch(DeviceTransportPlugin plugin,
            InboundMessage message) {
        Objects.requireNonNull(message, "message");
        String pluginId = plugin.descriptor().pluginId();
        if (!pluginId.equals(message.transport())) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Inbound transport identity " + message.transport() + " does not match plugin " + pluginId));
        }
        try {
            return Objects.requireNonNull(inboundHandler.onMessage(message),
                    "inbound handler returned null completion stage");
        } catch (RuntimeException failure) {
            return java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
    }

    public synchronized void stop() {
        if (!running && started.isEmpty()) return;
        RuntimeException failure = stopStarted();
        running = false;
        if (failure != null) throw failure;
    }

    private RuntimeException stopStarted() {
        Collections.reverse(started);
        RuntimeException failure = null;
        for (DeviceTransportPlugin plugin : started) {
            try {
                plugin.stop();
            } catch (RuntimeException stopFailure) {
                if (failure == null) {
                    failure = new IllegalStateException("Unable to stop one or more IoT plugins");
                }
                failure.addSuppressed(stopFailure);
            }
        }
        started.clear();
        return failure;
    }

    public synchronized boolean isRunning() { return running; }

    public List<PluginDescriptor> installedPlugins() {
        return registry.all().stream().map(DeviceTransportPlugin::descriptor)
                .sorted(java.util.Comparator.comparing(PluginDescriptor::pluginId)).toList();
    }

    @Override public void close() { stop(); }
}
