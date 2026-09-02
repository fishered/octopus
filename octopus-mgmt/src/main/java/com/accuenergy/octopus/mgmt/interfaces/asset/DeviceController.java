package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.asset.DeviceManagementService;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public final class DeviceController {
    private final DeviceManagementService devices;
    public DeviceController(DeviceManagementService devices) { this.devices = devices; }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_device:create') or hasAuthority('PERM_platform:all')")
    public DeviceResponse register(Authentication authentication, @Valid @RequestBody RegisterDeviceRequest request) {
        Device device = devices.register(principal(authentication), new DeviceManagementService.RegisterDevice(
                request.organizationId(), request.facilityId(), request.deviceTypeId(), request.thingModelId(),
                request.modelVersion(), request.code(), request.displayName()));
        return response(device);
    }

    @GetMapping("/{deviceId}")
    @PreAuthorize("hasAuthority('PERM_device:view') or hasAuthority('PERM_platform:all')")
    public DeviceResponse get(Authentication authentication, @PathVariable UUID deviceId) {
        return response(devices.get(principal(authentication), deviceId));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    private static DeviceResponse response(Device d) {
        return new DeviceResponse(d.id(), d.organizationId(), d.facilityId().orElse(null), d.deviceTypeId(),
                d.thingModelId(), d.modelVersion(), d.code(), d.displayName(), d.status().name(), d.version(),
                d.createdAt(), d.updatedAt());
    }

    public record RegisterDeviceRequest(@NotNull UUID organizationId, UUID facilityId,
            @NotNull UUID deviceTypeId, @NotNull UUID thingModelId, @Positive long modelVersion,
            @NotBlank String code, @NotBlank String displayName) { }
    public record DeviceResponse(UUID id, UUID organizationId, UUID facilityId, UUID deviceTypeId,
            UUID thingModelId, long modelVersion, String code, String displayName, String status,
            long version, Instant createdAt, Instant updatedAt) { }
}

