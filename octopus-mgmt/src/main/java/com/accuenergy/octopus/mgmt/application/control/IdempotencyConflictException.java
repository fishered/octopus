package com.accuenergy.octopus.mgmt.application.control;

public final class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("Idempotency key was already used for a different command"); }
}
