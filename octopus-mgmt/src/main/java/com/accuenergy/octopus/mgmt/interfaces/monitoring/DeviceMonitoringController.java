package com.accuenergy.octopus.mgmt.interfaces.monitoring;

import com.accuenergy.octopus.api.control.DevicePresenceSnapshot;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceMonitoringService;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredShadowService;
import com.accuenergy.octopus.mgmt.domain.monitoring.DeviceShadow;
import com.accuenergy.octopus.mgmt.domain.monitoring.DesiredShadowRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/monitoring")
public final class DeviceMonitoringController {
    private final DeviceMonitoringService monitoring;
    private final DeviceDesiredShadowService desired;
    private final ObjectMapper objectMapper;

    public DeviceMonitoringController(DeviceMonitoringService monitoring, DeviceDesiredShadowService desired,
            ObjectMapper objectMapper) {
        this.monitoring = monitoring;
        this.desired = desired;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/presence")
    @PreAuthorize("hasAuthority('PERM_device:view') or hasAuthority('PERM_platform:all')")
    public PresenceResponse presence(Authentication authentication, @PathVariable UUID deviceId) {
        DevicePresenceSnapshot snapshot = monitoring.presence(principal(authentication), deviceId);
        return new PresenceResponse(snapshot.deviceId(), snapshot.status(), snapshot.lastSeenAt(),
                snapshot.leaseExpiresAt());
    }

    @GetMapping("/shadow")
    @PreAuthorize("hasAuthority('PERM_device:view') or hasAuthority('PERM_platform:all')")
    public ShadowResponse shadow(Authentication authentication, @PathVariable UUID deviceId) {
        DeviceShadow shadow = monitoring.shadow(principal(authentication), deviceId);
        return new ShadowResponse(shadow.deviceId(), shadow.shadowVersion(), parseState(shadow.stateJson()),
                shadow.reportedAt(), shadow.receivedAt(), shadow.appliedDesiredVersion().orElse(null));
    }

    @GetMapping("/shadow/desired")
    @PreAuthorize("hasAuthority('PERM_device:view') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DesiredShadowResponse> desired(Authentication authentication,
            @PathVariable UUID deviceId) {
        DesiredShadowRequest request = desired.get(principal(authentication), deviceId);
        return desiredResponse(request);
    }

    @PutMapping("/shadow/desired")
    @PreAuthorize("hasAuthority('PERM_device:operate') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DesiredShadowResponse> requestDesired(Authentication authentication,
            @PathVariable UUID deviceId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody DesiredShadowBody body) {
        DesiredShadowRequest request = desired.request(principal(authentication), deviceId,
                new DeviceDesiredShadowService.RequestDesiredState(idempotencyKey, parseVersion(ifMatch),
                        body.state().toString(), Duration.ofSeconds(body.ttlSeconds())));
        return desiredResponse(request);
    }

    private JsonNode parseState(String stateJson) {
        try {
            return objectMapper.readTree(stateJson);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Persisted shadow JSON is invalid", impossible);
        }
    }

    private ResponseEntity<DesiredShadowResponse> desiredResponse(DesiredShadowRequest request) {
        DesiredShadowResponse response = new DesiredShadowResponse(request.requestId(), request.deviceId(),
                request.desiredVersion(), request.commandId(),
                request.status(), request.failureCode().orElse(null), request.requestedAt(), request.expiresAt(),
                request.appliedAt().orElse(null), request.statusUpdatedAt());
        return ResponseEntity.ok().eTag("\"" + request.desiredVersion() + "\"").body(response);
    }

    private static long parseVersion(String value) {
        if (value == null) throw new IllegalArgumentException("If-Match is required");
        String normalized = value.strip();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).strip();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            long version = Long.parseLong(normalized);
            if (version < 0) throw new NumberFormatException("negative");
            return version;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("If-Match must contain a non-negative desired version", invalid);
        }
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record PresenceResponse(UUID deviceId, DevicePresenceSnapshot.Status status,
            Instant lastSeenAt, Instant leaseExpiresAt) { }
    public record ShadowResponse(UUID deviceId, long shadowVersion, JsonNode reportedState,
            Instant reportedAt, Instant receivedAt, Long appliedDesiredVersion) { }
    public record DesiredShadowBody(@NotNull JsonNode state, @Min(1) @Max(600) long ttlSeconds) { }
    public record DesiredShadowResponse(UUID requestId, UUID deviceId, long desiredVersion,
            UUID commandId, DesiredShadowRequest.Status status, String failureCode,
            Instant requestedAt, Instant expiresAt, Instant appliedAt, Instant statusUpdatedAt) { }
}
