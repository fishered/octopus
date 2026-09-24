package com.accuenergy.octopus.ca.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.accuenergy.octopus.ca.application.CertificateSigningPort;
import com.accuenergy.octopus.ca.application.DeviceCertificateAuthorization;
import com.accuenergy.octopus.ca.application.DeviceIdentityRepository;
import com.accuenergy.octopus.ca.application.EmqxAuthorizationService;
import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EmqxAuthorizationControllerTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void invalidWebhookCredentialReturnsHttpOkWithExplicitDeny() {
        EmqxAuthorizationController controller = controller(new EmptyRepository(), "expected-key");
        var response = controller.authenticate(null,
                new EmqxAuthorizationController.EmqxRequest("serial-1", null, null, null));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("deny", response.getBody().get("result"));
    }

    @Test
    void malformedPeerCertificateCannotFallBackToSubmittedSerial() {
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        DeviceIdentityRepository repository = new EmptyRepository() {
            @Override
            public Optional<DeviceCertificateAuthorization> findCertificateAuthorization(String serial, Instant at) {
                return "serial-1".equals(serial)
                        ? Optional.of(new DeviceCertificateAuthorization(tenantId, deviceId, serial,
                                NOW.plusSeconds(60)))
                        : Optional.empty();
            }
        };
        EmqxAuthorizationController controller = controller(repository, "expected-key");
        var request = new EmqxAuthorizationController.EmqxRequest("serial-1", "not-a-certificate",
                "publish", "octopus/" + tenantId + "/devices/" + deviceId + "/telemetry");
        assertEquals("deny", controller.authorize("expected-key", request).getBody().get("result"));
    }

    private static EmqxAuthorizationController controller(DeviceIdentityRepository repository, String apiKey) {
        return new EmqxAuthorizationController(
                new EmqxAuthorizationService(repository, Clock.fixed(NOW, ZoneOffset.UTC)), apiKey);
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
        public Optional<DeviceCertificateAuthorization> findCertificateAuthorization(String serial, Instant now) {
            return Optional.empty();
        }
        public void saveIssued(DeviceIdentity identity, CertificateSigningPort.IssuedCertificate certificate,
                               String key, String fingerprint, Instant issuedAt) { }
        public void revoke(DeviceIdentity identity, String reason, Instant revokedAt) { }
    }
}
