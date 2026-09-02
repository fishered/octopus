package com.accuenergy.octopus.mgmt.application.monitoring;

public final class DesiredShadowIdempotencyConflictException extends RuntimeException {
    public DesiredShadowIdempotencyConflictException() { super("Idempotency-Key was reused for another desired state"); }
}
