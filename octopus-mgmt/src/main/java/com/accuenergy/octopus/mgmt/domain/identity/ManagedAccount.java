package com.accuenergy.octopus.mgmt.domain.identity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ManagedAccount(UUID id, String loginName, Optional<String> email, Optional<String> phoneE164,
                             String passwordHash, Status status, Instant passwordChangedAt,
                             Instant createdAt, Instant updatedAt) {
    public ManagedAccount {
        Objects.requireNonNull(id, "id");
        loginName = normalizeIdentity(loginName, "loginName", 128);
        email = Objects.requireNonNull(email, "email").map(value -> normalizeIdentity(value, "email", 320));
        phoneE164 = Objects.requireNonNull(phoneE164, "phoneE164").map(value -> requireText(value, "phoneE164", 20));
        if (email.isEmpty() && phoneE164.isEmpty()) {
            throw new IllegalArgumentException("Account requires email or phone");
        }
        if (phoneE164.isPresent() && !phoneE164.orElseThrow().matches("\\+[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("phoneE164 is invalid");
        }
        passwordHash = requireText(passwordHash, "passwordHash", 512);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ManagedAccount create(UUID id, String loginName, String email, String phoneE164,
                                        String passwordHash, Instant now) {
        return new ManagedAccount(id, loginName, Optional.ofNullable(email), Optional.ofNullable(phoneE164),
                passwordHash, Status.ACTIVE, now, now, now);
    }

    public enum Status { ACTIVE, SUSPENDED, DISABLED }

    private static String normalizeIdentity(String value, String name, int maximumLength) {
        return requireText(value, name, maximumLength).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}
