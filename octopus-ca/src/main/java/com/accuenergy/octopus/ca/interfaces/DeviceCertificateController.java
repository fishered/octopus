package com.accuenergy.octopus.ca.interfaces;

import com.accuenergy.octopus.ca.application.ClaimDeviceIdentityService;
import com.accuenergy.octopus.ca.application.IssueOperationalCertificateService;
import com.accuenergy.octopus.ca.application.ManufactureDeviceIdentityService;
import com.accuenergy.octopus.ca.application.RevokeDeviceIdentityService;
import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import com.accuenergy.octopus.common.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ca")
public final class DeviceCertificateController {
    private final ManufactureDeviceIdentityService manufacturing;
    private final ClaimDeviceIdentityService claiming;
    private final IssueOperationalCertificateService issuance;
    private final RevokeDeviceIdentityService revocation;

    public DeviceCertificateController(ManufactureDeviceIdentityService manufacturing,
                                       ClaimDeviceIdentityService claiming,
                                       IssueOperationalCertificateService issuance,
                                       RevokeDeviceIdentityService revocation) {
        this.manufacturing = manufacturing;
        this.claiming = claiming;
        this.issuance = issuance;
        this.revocation = revocation;
    }

    @PostMapping("/manufacturing/identities")
    @PreAuthorize("hasAuthority('PERM_ca:manufacture') or hasAuthority('PERM_platform:all')")
    public ManufacturedResponse manufacture(@Valid @RequestBody ManufactureRequest request) {
        var result = manufacturing.manufacture(new ManufactureDeviceIdentityService.Command(
                request.hardwareSerial(), request.manufacturer(), request.modelCode(), request.batchCode(),
                request.bootstrapPublicKeyFingerprint(), Duration.ofSeconds(request.bootstrapLifetimeSeconds())));
        return new ManufacturedResponse(identity(result.identity()), result.bootstrapToken(),
                result.bootstrapExpiresAt());
    }

    @PostMapping("/identities/{identityId}/claim")
    @PreAuthorize("hasAuthority('PERM_ca:claim') or hasAuthority('PERM_platform:all')")
    public IdentityResponse claim(@PathVariable UUID identityId, @Valid @RequestBody ClaimRequest request) {
        return identity(claiming.claim(identityId, tenantId(), request.bootstrapToken()));
    }

    @PostMapping("/identities/{identityId}/certificates")
    @PreAuthorize("hasAuthority('PERM_ca:issue') or hasAuthority('PERM_platform:all')")
    public CertificateResponse issue(@PathVariable UUID identityId,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody IssueCertificateRequest request) {
        byte[] csr;
        try {
            csr = Base64.getDecoder().decode(request.csrBase64());
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalArgumentException("csrBase64 is invalid", invalidBase64);
        }
        var certificate = issuance.issue(tenantId(), identityId, csr,
                Duration.ofSeconds(request.lifetimeSeconds()), idempotencyKey);
        return new CertificateResponse(certificate.certificateId(), certificate.serialNumber(),
                Base64.getEncoder().encodeToString(certificate.certificateChain()),
                certificate.notBefore(), certificate.notAfter());
    }

    @PutMapping("/identities/{identityId}/revocation")
    @PreAuthorize("hasAuthority('PERM_ca:revoke') or hasAuthority('PERM_platform:all')")
    public IdentityResponse revoke(@PathVariable UUID identityId, @Valid @RequestBody RevokeRequest request) {
        return identity(revocation.revoke(tenantId(), identityId, request.reason()));
    }

    private static UUID tenantId() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"))
                .value();
    }

    private static IdentityResponse identity(DeviceIdentity value) {
        return new IdentityResponse(value.identityId(), value.hardwareSerial(), value.manufacturer(),
                value.modelCode(), value.batchCode(), value.status(), value.tenantId().orElse(null),
                value.operationalCertificateSerial().orElse(null), value.certificateExpiresAt().orElse(null),
                value.claimedAt().orElse(null), value.version(), value.createdAt(), value.updatedAt());
    }

    public record ManufactureRequest(@NotBlank String hardwareSerial, @NotBlank String manufacturer,
                                     @NotBlank String modelCode, @NotBlank String batchCode,
                                     @NotBlank String bootstrapPublicKeyFingerprint,
                                     @Positive @Max(2592000) long bootstrapLifetimeSeconds) { }
    public record ClaimRequest(@NotBlank String bootstrapToken) { }
    public record IssueCertificateRequest(@NotBlank String csrBase64,
                                          @Positive @Max(7776000) long lifetimeSeconds) { }
    public record RevokeRequest(@NotBlank String reason) { }
    public record ManufacturedResponse(IdentityResponse identity, String bootstrapToken,
                                       Instant bootstrapExpiresAt) { }
    public record CertificateResponse(UUID certificateId, String serialNumber, String certificateChainBase64,
                                      Instant notBefore, Instant notAfter) { }
    public record IdentityResponse(UUID identityId, String hardwareSerial, String manufacturer,
                                   String modelCode, String batchCode, DeviceIdentity.Status status,
                                   UUID tenantId, String operationalCertificateSerial,
                                   Instant certificateExpiresAt, Instant claimedAt, long version,
                                   Instant createdAt, Instant updatedAt) { }
}
