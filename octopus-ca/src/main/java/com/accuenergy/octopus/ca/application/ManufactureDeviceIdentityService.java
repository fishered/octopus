package com.accuenergy.octopus.ca.application;

import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ManufactureDeviceIdentityService {
    private final DeviceIdentityRepository identities;
    private final BootstrapTokenService tokens;
    private final Clock clock;

    public ManufactureDeviceIdentityService(DeviceIdentityRepository identities,
                                            BootstrapTokenService tokens, Clock clock) {
        this.identities = identities;
        this.tokens = tokens;
        this.clock = clock;
    }

    public ManufacturedIdentity manufacture(Command command) {
        if (identities.hardwareSerialExists(command.hardwareSerial())) {
            throw new IllegalArgumentException("Hardware serial already exists");
        }
        Instant now = clock.instant();
        DeviceIdentity identity = DeviceIdentity.manufacture(UUID.randomUUID(), command.hardwareSerial(),
                command.manufacturer(), command.modelCode(), command.batchCode(),
                command.bootstrapPublicKeyFingerprint(), now);
        identity.enableBootstrap(now);
        BootstrapTokenService.IssuedToken token = tokens.issue();
        Instant expiresAt = now.plus(command.bootstrapLifetime());
        identities.create(identity, token.tokenHash(), expiresAt);
        return new ManufacturedIdentity(identity, token.rawToken(), expiresAt);
    }

    public record Command(String hardwareSerial, String manufacturer, String modelCode, String batchCode,
                          String bootstrapPublicKeyFingerprint, Duration bootstrapLifetime) {
        public Command {
            if (bootstrapLifetime == null || bootstrapLifetime.isNegative() || bootstrapLifetime.isZero()
                    || bootstrapLifetime.compareTo(Duration.ofDays(30)) > 0) {
                throw new IllegalArgumentException("bootstrapLifetime must be between 1 second and 30 days");
            }
        }
    }

    public record ManufacturedIdentity(DeviceIdentity identity, String bootstrapToken,
                                       Instant bootstrapExpiresAt) { }
}
