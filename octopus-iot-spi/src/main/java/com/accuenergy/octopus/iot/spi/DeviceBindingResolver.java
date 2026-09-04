package com.accuenergy.octopus.iot.spi;

import java.util.Optional;
import java.util.UUID;

/** Reads the versioned management projection used for per-device plugin routing. */
@FunctionalInterface
public interface DeviceBindingResolver {
    Optional<DeviceBinding> find(UUID tenantId, UUID deviceId);

    default DeviceBindingResolution resolve(UUID tenantId, UUID deviceId) {
        try {
            return find(tenantId, deviceId).map(binding ->
                    binding.status() == DeviceBinding.Status.DISABLED
                            ? DeviceBindingResolution.disabled(binding)
                            : DeviceBindingResolution.active(binding))
                    .orElseGet(DeviceBindingResolution::notFound);
        } catch (RuntimeException failure) {
            return DeviceBindingResolution.unavailable(failure);
        }
    }
}
