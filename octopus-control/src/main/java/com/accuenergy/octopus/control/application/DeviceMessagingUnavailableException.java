package com.accuenergy.octopus.control.application;

public final class DeviceMessagingUnavailableException extends RuntimeException {
    public DeviceMessagingUnavailableException(String message) { super(message); }
    public DeviceMessagingUnavailableException(String message, Throwable cause) { super(message, cause); }
}
