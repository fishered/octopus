package com.accuenergy.octopus.mgmt.application.asset;

/** Raised when one caller reuses an onboarding idempotency key for a different payload. */
public final class DeviceOnboardingIdempotencyConflictException extends RuntimeException {
    public DeviceOnboardingIdempotencyConflictException() {
        super("Idempotency-Key was already used for a different onboarding request");
    }
}
