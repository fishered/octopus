package com.accuenergy.octopus.mgmt.application.asset;

public final class DeviceActualAccessDeniedException extends RuntimeException {
    public DeviceActualAccessDeniedException() { super("Device hardware access denied"); }
}
