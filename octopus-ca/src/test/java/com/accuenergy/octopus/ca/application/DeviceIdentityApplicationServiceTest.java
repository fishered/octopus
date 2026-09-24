package com.accuenergy.octopus.ca.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceIdentityApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void manufacturingReturnsRawTokenOnceAndPersistsOnlyHash() {
        MemoryRepository repository = new MemoryRepository();
        BootstrapTokenService tokens = new BootstrapTokenService() {
            public IssuedToken issue() { return new IssuedToken("raw-secret", "stored-hash"); }
            public String hash(String rawToken) { return "stored-hash"; }
        };
        ManufactureDeviceIdentityService service = new ManufactureDeviceIdentityService(repository, tokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.manufacture(new ManufactureDeviceIdentityService.Command("SERIAL-1", "Octopus",
                "MODEL-1", "BATCH-1", "sha256:fingerprint", Duration.ofHours(1)));
        assertEquals("raw-secret", result.bootstrapToken());
        assertEquals("stored-hash", repository.bootstrapHash);
        assertNotEquals(result.bootstrapToken(), repository.bootstrapHash);
        assertEquals(DeviceIdentity.Status.BOOTSTRAP_READY, repository.identity.status());
    }

    @Test
    void issuanceIsIdempotentAndBindsTenantSan() {
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        DeviceIdentity identity = claimedIdentity(tenantId, deviceId);
        MemoryRepository repository = new MemoryRepository();
        repository.identity = identity;
        var certificate = new CertificateSigningPort.IssuedCertificate(UUID.randomUUID(), "serial-1", new byte[]{1, 2},
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        CertificateSigningPort signing = request -> {
            assertTrue(request.subjectAlternativeNames().contains("urn:octopus:tenant:" + tenantId));
            assertTrue(request.subjectAlternativeNames().contains("urn:octopus:device:" + deviceId));
            assertTrue(request.subjectAlternativeNames().contains("urn:octopus:device-identity:" + identity.identityId()));
            return certificate;
        };
        IssueOperationalCertificateService service = new IssueOperationalCertificateService(repository,
                csr -> true, signing, Clock.fixed(NOW, ZoneOffset.UTC));
        var first = service.issue(tenantId, identity.identityId(), new byte[]{9}, Duration.ofHours(1), "request-1");
        repository.existing = new DeviceIdentityRepository.IssuedCertificateRequest(first, repository.lastFingerprint);
        var retry = service.issue(tenantId, identity.identityId(), new byte[]{9}, Duration.ofHours(1), "request-1");
        assertEquals("serial-1", retry.serialNumber());
        assertEquals(1, repository.savedCertificates);
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentCsr() {
        UUID tenantId = UUID.randomUUID();
        DeviceIdentity identity = claimedIdentity(tenantId, UUID.randomUUID());
        MemoryRepository repository = new MemoryRepository();
        repository.identity = identity;
        var certificate = new CertificateSigningPort.IssuedCertificate(UUID.randomUUID(), "serial-2", new byte[]{1},
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        IssueOperationalCertificateService service = new IssueOperationalCertificateService(repository,
                csr -> true, request -> certificate, Clock.fixed(NOW, ZoneOffset.UTC));
        service.issue(tenantId, identity.identityId(), new byte[]{1}, Duration.ofHours(1), "request-conflict");
        repository.existing = new DeviceIdentityRepository.IssuedCertificateRequest(certificate,
                repository.lastFingerprint);
        assertThrows(IllegalStateException.class, () -> service.issue(tenantId, identity.identityId(),
                new byte[]{2}, Duration.ofHours(1), "request-conflict"));
    }

    @Test
    void invalidProofOfPossessionDoesNotReachSigner() {
        UUID tenantId = UUID.randomUUID();
        MemoryRepository repository = new MemoryRepository();
        repository.identity = claimedIdentity(tenantId, UUID.randomUUID());
        IssueOperationalCertificateService service = new IssueOperationalCertificateService(repository,
                csr -> false, request -> { throw new AssertionError("signer must not be called"); },
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(SecurityException.class, () -> service.issue(tenantId, repository.identity.identityId(),
                new byte[]{1}, Duration.ofHours(1), "request-2"));
    }

    private static DeviceIdentity claimedIdentity(UUID tenantId, UUID deviceId) {
        DeviceIdentity identity = DeviceIdentity.manufacture(UUID.randomUUID(), "SERIAL-X", "Octopus",
                "MODEL-1", "BATCH-1", "sha256:fingerprint", NOW);
        identity.enableBootstrap(NOW);
        identity.claim(tenantId, deviceId, NOW);
        return identity;
    }

    private static final class MemoryRepository implements DeviceIdentityRepository {
        private DeviceIdentity identity;
        private String bootstrapHash;
        private DeviceIdentityRepository.IssuedCertificateRequest existing;
        private String lastFingerprint;
        private int savedCertificates;
        public boolean hardwareSerialExists(String hardwareSerial) { return false; }
        public void create(DeviceIdentity value, String tokenHash, Instant expiresAt) {
            identity = value; bootstrapHash = tokenHash;
        }
        public Optional<DeviceIdentity> claim(UUID id, String hash, UUID tenant, UUID device, Instant now) {
            if (identity == null || !hash.equals(bootstrapHash)) return Optional.empty();
            identity.claim(tenant, device, now); return Optional.of(identity);
        }
        public Optional<DeviceIdentity> find(UUID id) { return Optional.ofNullable(identity); }
        public Optional<DeviceIdentity> findForTenant(UUID tenant, UUID id) {
            return identity != null && identity.tenantId().filter(tenant::equals).isPresent()
                    ? Optional.of(identity) : Optional.empty();
        }
        public Optional<DeviceIdentityRepository.IssuedCertificateRequest> findIssuedByIdempotency(
                UUID tenant, UUID id, String key) { return Optional.ofNullable(existing); }
        public Optional<DeviceCertificateAuthorization> findCertificateAuthorization(String serial, Instant now) {
            return Optional.empty();
        }
        public void saveIssued(DeviceIdentity value, CertificateSigningPort.IssuedCertificate certificate,
                               String key, String csrFingerprint, Instant issuedAt) {
            identity = value;
            existing = new DeviceIdentityRepository.IssuedCertificateRequest(certificate, csrFingerprint);
            lastFingerprint = csrFingerprint;
            savedCertificates++;
        }
        public void revoke(DeviceIdentity value, String reason, Instant revokedAt) { identity = value; }
    }
}
