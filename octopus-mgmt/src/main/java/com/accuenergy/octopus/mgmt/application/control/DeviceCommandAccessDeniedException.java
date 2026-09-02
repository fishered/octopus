package com.accuenergy.octopus.mgmt.application.control;

public final class DeviceCommandAccessDeniedException extends RuntimeException {
    public DeviceCommandAccessDeniedException() { super("Device command access denied"); }
}
