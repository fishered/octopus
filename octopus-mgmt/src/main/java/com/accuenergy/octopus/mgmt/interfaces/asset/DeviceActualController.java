package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.asset.DeviceActualManagementService;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/actual")
public final class DeviceActualController {
    private final DeviceActualManagementService actuals;

    public DeviceActualController(DeviceActualManagementService actuals) { this.actuals = actuals; }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_device:configure') or hasAuthority('PERM_platform:all')")
    public DeviceActualResponse commission(Authentication authentication, @PathVariable UUID deviceId,
                                           @Valid @RequestBody CommissionRequest request) {
        return response(actuals.commission(principal(authentication),
                new DeviceActualManagementService.CommissionDeviceActual(deviceId, request.hardwareSerial(),
                        request.manufacturer(), request.firmwareVersion(), request.certificateId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_device:view') or hasAuthority('PERM_platform:all')")
    public DeviceActualResponse get(Authentication authentication, @PathVariable UUID deviceId) {
        return response(actuals.get(principal(authentication), deviceId));
    }

    @PutMapping("/certificate")
    @PreAuthorize("hasAuthority('PERM_device:configure') or hasAuthority('PERM_platform:all')")
    public DeviceActualResponse bindCertificate(Authentication authentication, @PathVariable UUID deviceId,
                                                @Valid @RequestBody BindCertificateRequest request) {
        return response(actuals.bindCertificate(principal(authentication), deviceId, request.certificateId()));
    }

    private static DeviceActualResponse response(DeviceActual actual) {
        return new DeviceActualResponse(actual.id(), actual.deviceId(), actual.hardwareSerial(),
                actual.manufacturer().orElse(null), actual.firmwareVersion().orElse(null),
                actual.certificateId().orElse(null), actual.connectivityStatus(),
                actual.lastSeenAt().orElse(null), actual.commissionedAt(), actual.version(),
                actual.createdAt(), actual.updatedAt());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CommissionRequest(@NotBlank String hardwareSerial, String manufacturer,
                                    String firmwareVersion, UUID certificateId) { }
    public record BindCertificateRequest(@NotNull UUID certificateId) { }
    public record DeviceActualResponse(UUID id, UUID deviceId, String hardwareSerial, String manufacturer,
                                       String firmwareVersion, UUID certificateId,
                                       DeviceActual.ConnectivityStatus connectivityStatus, Instant lastSeenAt,
                                       Instant commissionedAt, long version, Instant createdAt, Instant updatedAt) { }
}
