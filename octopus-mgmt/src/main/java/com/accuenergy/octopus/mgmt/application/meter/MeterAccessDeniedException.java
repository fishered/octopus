package com.accuenergy.octopus.mgmt.application.meter;

public final class MeterAccessDeniedException extends RuntimeException {
    public MeterAccessDeniedException() { super("Meter access denied"); }
}
