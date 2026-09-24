package com.accuenergy.octopus.ca.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Broker authorization policy based exclusively on the certificate verified by EMQX. */
public final class EmqxAuthorizationService {
    private final DeviceIdentityRepository identities;
    private final Clock clock;

    public EmqxAuthorizationService(DeviceIdentityRepository identities, Clock clock) {
        this.identities = identities;
        this.clock = clock;
    }

    public boolean authenticate(String certificateSerial) {
        return authorization(certificateSerial).isPresent();
    }

    public boolean authorize(String certificateSerial, String action, String topic) {
        Optional<DeviceCertificateAuthorization> verified = authorization(certificateSerial);
        if (verified.isEmpty() || action == null || topic == null) return false;
        return EmqxTopicPolicy.allows(verified.orElseThrow(), action, topic);
    }

    private Optional<DeviceCertificateAuthorization> authorization(String certificateSerial) {
        if (certificateSerial == null || certificateSerial.isBlank() || certificateSerial.length() > 200) {
            return Optional.empty();
        }
        try {
            return identities.findCertificateAuthorization(certificateSerial.strip(), clock.instant());
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    static final class EmqxTopicPolicy {
        private EmqxTopicPolicy() { }

        static boolean allows(DeviceCertificateAuthorization identity, String action, String topic) {
            String normalizedAction = action.strip().toLowerCase(java.util.Locale.ROOT);
            String[] parts = topic.split("/", -1);
            if (parts.length < 5 || !"octopus".equals(parts[0]) || !"devices".equals(parts[2])) return false;
            UUID tenant;
            UUID device;
            try {
                tenant = UUID.fromString(parts[1]);
                device = UUID.fromString(parts[3]);
            } catch (IllegalArgumentException malformedIdentity) {
                return false;
            }
            if (!identity.tenantId().equals(tenant) || !identity.deviceId().equals(device)) return false;
            if ("publish".equals(normalizedAction)) {
                return (parts.length == 5 && "telemetry".equals(parts[4]))
                        || (parts.length == 6 && "shadow".equals(parts[4]) && "reported".equals(parts[5]))
                        || (parts.length == 6 && "command-results".equals(parts[4]) && isUuid(parts[5]));
            }
            if ("subscribe".equals(normalizedAction)) {
                return parts.length == 6 && "commands".equals(parts[4])
                        && ("#".equals(parts[5]) || parts[5].matches("[A-Za-z][A-Za-z0-9._-]{0,63}"));
            }
            return false;
        }

        private static boolean isUuid(String value) {
            try { UUID.fromString(value); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }
    }
}
