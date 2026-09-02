package com.accuenergy.octopus.mgmt.domain.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuthSession {
    private final UUID sessionId;
    private final UUID accountId;
    private final Instant absoluteExpiresAt;
    private Instant idleExpiresAt;
    private long refreshGeneration;
    private Status status;

    private AuthSession(UUID sessionId, UUID accountId, Instant idleExpiresAt, Instant absoluteExpiresAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.idleExpiresAt = Objects.requireNonNull(idleExpiresAt, "idleExpiresAt");
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
        if (idleExpiresAt.isAfter(absoluteExpiresAt)) {
            throw new IllegalArgumentException("Idle expiry cannot exceed absolute expiry");
        }
        this.status = Status.ACTIVE;
    }

    public static AuthSession start(UUID sessionId, UUID accountId, Instant now,
                                    Duration idleTimeout, Duration absoluteTimeout) {
        requirePositive(idleTimeout, "idleTimeout");
        requirePositive(absoluteTimeout, "absoluteTimeout");
        if (idleTimeout.compareTo(absoluteTimeout) > 0) {
            throw new IllegalArgumentException("Idle timeout cannot exceed absolute timeout");
        }
        return new AuthSession(sessionId, accountId, now.plus(idleTimeout), now.plus(absoluteTimeout));
    }

    /** Rotates the refresh-token generation and extends idle expiry without extending absolute lifetime. */
    public long renew(Instant now, Duration idleTimeout, long presentedGeneration) {
        requireUsable(now);
        requirePositive(idleTimeout, "idleTimeout");
        if (presentedGeneration != refreshGeneration) {
            status = Status.REVOKED;
            throw new RefreshTokenReuseException("Refresh token generation was already rotated");
        }
        refreshGeneration++;
        Instant candidate = now.plus(idleTimeout);
        idleExpiresAt = candidate.isBefore(absoluteExpiresAt) ? candidate : absoluteExpiresAt;
        return refreshGeneration;
    }

    public void revoke() {
        status = Status.REVOKED;
    }

    public boolean isUsableAt(Instant now) {
        return status == Status.ACTIVE && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
    }

    public long refreshGeneration() { return refreshGeneration; }
    public Status status() { return status; }
    public Instant idleExpiresAt() { return idleExpiresAt; }
    public Instant absoluteExpiresAt() { return absoluteExpiresAt; }
    public UUID sessionId() { return sessionId; }
    public UUID accountId() { return accountId; }

    private void requireUsable(Instant now) {
        if (!isUsableAt(Objects.requireNonNull(now, "now"))) {
            if (status == Status.ACTIVE) status = Status.EXPIRED;
            throw new IllegalStateException("Session is not active");
        }
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }

    public enum Status { ACTIVE, REVOKED, EXPIRED }
}
