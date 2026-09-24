package com.accuenergy.octopus.ca.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmqxAuthorizationServiceTest {
    @Test
    void permitsOnlyTheCertificateOwnersNamespace() {
        UUID tenant = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        DeviceCertificateAuthorization verified = new DeviceCertificateAuthorization(tenant, device, "serial-1",
                now.plusSeconds(60));
        DeviceIdentityRepository repository = new EmptyRepository() {
            @Override public Optional<DeviceCertificateAuthorization> findCertificateAuthorization(String serial, Instant at) {
                return "serial-1".equals(serial) ? Optional.of(verified) : Optional.empty();
            }
        };
        EmqxAuthorizationService service = new EmqxAuthorizationService(repository, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(service.authenticate("serial-1"));
        assertTrue(service.authorize("serial-1", "publish", "octopus/" + tenant + "/devices/" + device + "/telemetry"));
        assertTrue(service.authorize("serial-1", "subscribe", "octopus/" + tenant + "/devices/" + device + "/commands/switch"));
        assertFalse(service.authorize("serial-1", "publish", "octopus/" + UUID.randomUUID() + "/devices/" + device + "/telemetry"));
        assertFalse(service.authorize("serial-1", "subscribe", "octopus/" + tenant + "/devices/" + device + "/telemetry"));
    }

    private static class EmptyRepository implements DeviceIdentityRepository {
        public boolean hardwareSerialExists(String value) { return false; }
        public void create(DeviceIdentity identity, String hash, Instant expiresAt) { }
        public Optional<DeviceIdentity> claim(UUID id, String hash, UUID tenant, UUID device, Instant now) {
            return Optional.empty();
        }
        public Optional<DeviceIdentity> find(UUID id) { return Optional.empty(); }
        public Optional<DeviceIdentity> findForTenant(UUID tenant, UUID id) { return Optional.empty(); }
        public Optional<IssuedCertificateRequest> findIssuedByIdempotency(UUID tenant, UUID id, String key) {
            return Optional.empty();
        }
        public Optional<DeviceCertificateAuthorization> findCertificateAuthorization(String serial, Instant now) { return Optional.empty(); }
        public void saveIssued(DeviceIdentity identity, CertificateSigningPort.IssuedCertificate certificate, String key, String fingerprint, Instant issuedAt) { }
        public void revoke(DeviceIdentity identity, String reason, Instant revokedAt) { }
    }
}
