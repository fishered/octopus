package com.accuenergy.octopus.ai.spi;

import java.util.Set;

/** Stable metadata used to validate and select an installed AI provider. */
public record AiProviderDescriptor(String providerId, String version, int spiVersion,
        Set<AiCapability> capabilities, Set<String> models, String configurationSchema) {
    public static final int CURRENT_SPI_VERSION = 1;

    public AiProviderDescriptor {
        requireToken(providerId, "providerId");
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("version is invalid");
        }
        if (spiVersion < 1) throw new IllegalArgumentException("spiVersion must be positive");
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        capabilities = Set.copyOf(capabilities);
        if (models == null || models.isEmpty()) throw new IllegalArgumentException("models must not be empty");
        models = models.stream().map(model -> {
            if (model == null || model.isBlank() || model.length() > 128) {
                throw new IllegalArgumentException("model is invalid");
            }
            return model.strip();
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
