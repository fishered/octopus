package com.accuenergy.octopus.mgmt.application.monitoring;

public final class DesiredShadowVersionConflictException extends RuntimeException {
    public DesiredShadowVersionConflictException() { super("Desired shadow version does not match If-Match"); }
}
