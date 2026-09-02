package com.accuenergy.octopus.mgmt.application.asset;

public final class DeviceAccessDeniedException extends SecurityException {
    public DeviceAccessDeniedException() { super("Device access is denied"); }
}

