package com.accuenergy.octopus.iot.spi;

import java.util.Set;
import java.util.UUID;

/** Immutable metadata used to validate and route an installed plugin. */
public record PluginDescriptor(String pluginId, String version, int spiVersion,
        Set<MessageKind> messageKinds, String configurationSchema) {
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
