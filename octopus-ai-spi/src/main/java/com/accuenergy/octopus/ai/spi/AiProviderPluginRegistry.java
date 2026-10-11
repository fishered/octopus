package com.accuenergy.octopus.ai.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Immutable AI provider registry; duplicate IDs and incompatible SPI versions fail at startup. */
public final class AiProviderPluginRegistry {
    private final Map<String, AiProviderPlugin> providers;

    public AiProviderPluginRegistry(List<? extends AiProviderPlugin> providers) {
        Objects.requireNonNull(providers, "providers");
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> validate(Objects.requireNonNull(provider, "provider")),
                Function.identity(), (left, right) -> {
                    throw new IllegalArgumentException("Duplicate AI provider: " + left.descriptor().providerId());
                }));
    }

    public Optional<AiProviderPlugin> find(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    public AiProviderPlugin require(String providerId) {
        return find(providerId).orElseThrow(() ->
                new IllegalArgumentException("AI provider is not installed: " + providerId));
    }

    public List<AiProviderDescriptor> descriptors() {
        return providers.values().stream().map(AiProviderPlugin::descriptor)
                .sorted(java.util.Comparator.comparing(AiProviderDescriptor::providerId)).toList();
    }

    private static String validate(AiProviderPlugin provider) {
        AiProviderDescriptor descriptor = Objects.requireNonNull(provider.descriptor(), "provider descriptor");
        if (descriptor.spiVersion() != AiProviderDescriptor.CURRENT_SPI_VERSION) {
            throw new IllegalArgumentException("Unsupported AI provider SPI version " + descriptor.spiVersion()
                    + " for " + descriptor.providerId() + "; expected " + AiProviderDescriptor.CURRENT_SPI_VERSION);
        }
        return descriptor.providerId();
    }
}
