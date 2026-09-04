package com.accuenergy.octopus.iot.spi;

import java.util.Objects;

/** Explicit result of resolving a device binding; absence and infrastructure failure are different states. */
public record DeviceBindingResolution(Status status, DeviceBinding binding, Throwable failure) {
    public DeviceBindingResolution {
        Objects.requireNonNull(status, "status");
        if (status == Status.ACTIVE || status == Status.DISABLED) {
            Objects.requireNonNull(binding, "binding");
        } else if (binding != null) {
            throw new IllegalArgumentException("Non-binding resolution cannot carry a binding");
        }
        if (status != Status.UNAVAILABLE && failure != null) {
            throw new IllegalArgumentException("Only unavailable resolution can carry a failure");
        }
    }

    public static DeviceBindingResolution active(DeviceBinding binding) {
        return new DeviceBindingResolution(Status.ACTIVE, binding, null);
    }

    public static DeviceBindingResolution disabled(DeviceBinding binding) {
        return new DeviceBindingResolution(Status.DISABLED, binding, null);
    }

    public static DeviceBindingResolution notFound() {
        return new DeviceBindingResolution(Status.NOT_FOUND, null, null);
    }

    public static DeviceBindingResolution unavailable(Throwable failure) {
        return new DeviceBindingResolution(Status.UNAVAILABLE, null, Objects.requireNonNull(failure, "failure"));
    }

    public enum Status { ACTIVE, DISABLED, NOT_FOUND, UNAVAILABLE }
}
