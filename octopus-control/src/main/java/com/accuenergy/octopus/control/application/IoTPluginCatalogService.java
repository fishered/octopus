package com.accuenergy.octopus.control.application;

import com.accuenergy.octopus.iot.spi.DeviceTransportPlugin;
import com.accuenergy.octopus.iot.spi.DeviceTransportPluginRegistry;
import com.accuenergy.octopus.iot.spi.MessageKind;
import com.accuenergy.octopus.iot.spi.PluginDescriptor;
import com.accuenergy.octopus.iot.spi.PluginHealth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Sanitized operator catalog and fail-fast compatibility check for onboarding choices. */
@Service
public final class IoTPluginCatalogService {
    private final DeviceTransportPluginRegistry plugins;

    public IoTPluginCatalogService(DeviceTransportPluginRegistry plugins) {
        this.plugins = plugins;
    }

    public List<PluginEntry> catalog() {
        return plugins.all().stream()
                .sorted(Comparator.comparing(plugin -> plugin.descriptor().pluginId()))
                .map(this::entry)
                .toList();
    }

    public PreflightResult preflight(String pluginId, String codecId, Set<MessageKind> requiredKinds) {
        requireToken(pluginId, "pluginId");
        requireToken(codecId, "codecId");
        if (requiredKinds == null || requiredKinds.isEmpty()) {
            throw new IllegalArgumentException("requiredKinds must not be empty");
        }
        Set<MessageKind> kinds = Set.copyOf(requiredKinds);
        DeviceTransportPlugin plugin = plugins.find(pluginId).orElse(null);
        if (plugin == null) {
            return new PreflightResult(false, pluginId, codecId, kinds, "NOT_INSTALLED",
                    List.of("PLUGIN_NOT_INSTALLED"), List.of());
        }

        PluginDescriptor descriptor = plugin.descriptor();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!descriptor.codecIds().contains(codecId)) errors.add("CODEC_NOT_SUPPORTED");
        if (!descriptor.messageKinds().containsAll(kinds)) errors.add("MESSAGE_KIND_NOT_SUPPORTED");

        String status;
        try {
            PluginHealth.Status health = plugin.health().status();
            status = health.name();
            if (health == PluginHealth.Status.DOWN || health == PluginHealth.Status.STOPPED) {
                errors.add("PLUGIN_UNAVAILABLE");
            } else if (health == PluginHealth.Status.DEGRADED || health == PluginHealth.Status.STARTING) {
                warnings.add("PLUGIN_" + health.name());
            }
        } catch (RuntimeException failure) {
            status = PluginHealth.Status.DOWN.name();
            errors.add("PLUGIN_HEALTH_CHECK_FAILED");
        }
        return new PreflightResult(errors.isEmpty(), pluginId, codecId, kinds, status,
                List.copyOf(errors), List.copyOf(warnings));
    }

    private PluginEntry entry(DeviceTransportPlugin plugin) {
        PluginDescriptor descriptor = plugin.descriptor();
        String status;
        try {
            status = plugin.health().status().name();
        } catch (RuntimeException failure) {
            status = PluginHealth.Status.DOWN.name();
        }
        return new PluginEntry(descriptor.pluginId(), descriptor.version(), descriptor.spiVersion(),
                descriptor.messageKinds(), descriptor.codecIds(), descriptor.configurationSchema(), status);
    }

    private static void requireToken(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    public record PluginEntry(String pluginId, String version, int spiVersion,
            Set<MessageKind> messageKinds, Set<String> codecIds, String configurationSchema, String status) {
        public PluginEntry {
            Objects.requireNonNull(pluginId, "pluginId");
            messageKinds = Set.copyOf(messageKinds);
            codecIds = Set.copyOf(codecIds);
        }
    }

    public record PreflightResult(boolean compatible, String pluginId, String codecId,
            Set<MessageKind> requiredKinds, String status, List<String> errors, List<String> warnings) {
        public PreflightResult {
            requiredKinds = Set.copyOf(requiredKinds);
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }
}
