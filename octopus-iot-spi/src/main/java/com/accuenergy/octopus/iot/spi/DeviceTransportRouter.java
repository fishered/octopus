package com.accuenergy.octopus.iot.spi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Selects the active transport and codec explicitly bound to a device. */
public final class DeviceTransportRouter {
    private final DeviceTransportPluginRegistry registry;
    private final DeviceBindingResolver bindings;
    private final ProtocolCodecRegistry codecs;

    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings) {
        this(registry, bindings, new ProtocolCodecRegistry(java.util.List.of(new PassthroughJsonCodec())));
    }

    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings,
            ProtocolCodecRegistry codecs) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.codecs = Objects.requireNonNull(codecs, "codecs");
    }

    /** @deprecated Device bindings are fail-closed; no default plugin fallback is used. */
    @Deprecated(forRemoval = true)
    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings,
            String ignoredDefaultPluginId) {
        this(registry, bindings);
    }

    /** @deprecated Device bindings are fail-closed; no default plugin fallback is used. */
    @Deprecated(forRemoval = true)
    public DeviceTransportRouter(DeviceTransportPluginRegistry registry, DeviceBindingResolver bindings,
            ProtocolCodecRegistry codecs, String ignoredDefaultPluginId) {
        this(registry, bindings, codecs);
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
