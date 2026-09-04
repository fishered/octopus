package com.accuenergy.octopus.iot.spi;

import java.util.Set;
import java.util.Objects;

/** Immutable metadata used to validate and route an installed plugin. */
public record PluginDescriptor(String pluginId, String version, int spiVersion,
        Set<MessageKind> messageKinds, Set<String> codecIds, String configurationSchema) {
    public PluginDescriptor(String pluginId, String version, int spiVersion,
            Set<MessageKind> messageKinds, String configurationSchema) {
        this(pluginId, version, spiVersion, messageKinds, Set.of("json"), configurationSchema);
    }

    public PluginDescriptor {
        requireToken(pluginId, "pluginId");
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("version is invalid");
        }
        if (spiVersion < 1) throw new IllegalArgumentException("spiVersion must be positive");
        if (messageKinds == null || messageKinds.isEmpty()) {
            throw new IllegalArgumentException("messageKinds must not be empty");
        }
        messageKinds = Set.copyOf(messageKinds);
        if (codecIds == null || codecIds.isEmpty()) {
            throw new IllegalArgumentException("codecIds must not be empty");
        }
        codecIds = codecIds.stream().map(value -> {
            requireToken(value, "codecId");
            return value;
        }).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (configurationSchema == null || configurationSchema.isBlank()) {
            throw new IllegalArgumentException("configurationSchema must not be blank");
        }
    }

    private static void requireToken(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
