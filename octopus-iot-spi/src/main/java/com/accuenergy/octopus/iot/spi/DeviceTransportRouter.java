package com.accuenergy.octopus.iot.spi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Selects a device-bound plugin, falling back to the configured platform plugin. */
public final class DeviceTransportRouter {
    private final DeviceTransportPluginRegistry registry;
    private final DeviceBindingResolver bindings;
    private final ProtocolCodecRegistry codecs;
    private final String defaultPluginId;

    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings,
            String defaultPluginId) {
        this(registry, bindings, new ProtocolCodecRegistry(java.util.List.of(new PassthroughJsonCodec())), defaultPluginId);
    }

    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings,
            ProtocolCodecRegistry codecs, String defaultPluginId) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.codecs = Objects.requireNonNull(codecs, "codecs");
        if (defaultPluginId == null || defaultPluginId.isBlank()) throw new IllegalArgumentException("defaultPluginId is required");
        this.defaultPluginId = defaultPluginId.strip();
    }

    public CompletionStage<PublishReceipt> publish(OutboundMessage message) {
        DeviceBindingResolution resolution = bindings.resolve(message.tenantId(), message.deviceId());
        if (resolution.status() == DeviceBindingResolution.Status.NOT_FOUND) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "No active device binding is available; refusing default plugin fallback"));
        }
        if (resolution.status() == DeviceBindingResolution.Status.UNAVAILABLE) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Device binding resolution is unavailable", resolution.failure()));
        }
        DeviceBinding binding = resolution.binding();
        if (!binding.tenantId().equals(message.tenantId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Device binding tenant does not match message"));
        }
        if (resolution.status() == DeviceBindingResolution.Status.DISABLED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Device connector binding is disabled"));
        }
        String pluginId = binding.pluginId();
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
        if (!plugin.descriptor().codecIds().contains(binding.codecId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "IoT plugin does not support codec " + binding.codecId() + ": " + pluginId));
        }
        ProtocolCodec codec;
        try {
            codec = codecs.require(binding.codecId());
        } catch (RuntimeException missing) {
            return CompletableFuture.failedFuture(missing);
        }
        try {
            byte[] encoded = codec.encode(message);
            OutboundMessage encodedMessage = new OutboundMessage(message.kind(), message.tenantId(), message.deviceId(),
                    message.correlationId(), message.operation(), encoded, message.requestedAt(), message.expiresAt(),
                    message.contentType());
            return plugin.publish(encodedMessage);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static final class PassthroughJsonCodec implements ProtocolCodec {
        @Override public String codecId() { return "json"; }
        @Override public boolean supports(InboundMessage message, MessageKind kind) { return true; }
        @Override public DecodedPayload decode(InboundMessage message, MessageKind kind) {
            return new DecodedPayload(kind, message.payload());
        }
        @Override public byte[] encode(OutboundMessage message) { return message.payload(); }
    }
}
