package com.accuenergy.octopus.mgmt.application.monitoring;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import com.accuenergy.octopus.mgmt.domain.monitoring.DesiredShadowRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public final class DeviceDesiredShadowService {
    private static final Duration MAX_TTL = Duration.ofMinutes(10);
    private final DeviceRepository devices;
    private final DeviceDesiredStateValidator validator;
    private final DeviceDesiredShadowRepository desired;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public DeviceDesiredShadowService(DeviceRepository devices, DeviceDesiredStateValidator validator,
            DeviceDesiredShadowRepository desired, AuthorizationPolicy authorization, Clock clock) {
        this.devices = devices;
        this.validator = validator;
        this.desired = desired;
        this.authorization = authorization;
        this.clock = clock;
    }

    public DesiredShadowRequest request(AuthenticatedPrincipal principal, UUID deviceId, RequestDesiredState command) {
        DeviceRepository.DeviceDetails details = requireAccess(principal, deviceId, "device:operate",
                ResourceAction.OPERATE);
        validate(command);
        DeviceDesiredStateValidator.ValidatedState state = validator.validate(deviceId, command.stateJson());
        String idempotencyKey = command.idempotencyKey().strip();
        String requestDigest = sha256((command.expectedVersion() + "\n" + command.ttl().toMillis() + "\n"
                + state.canonicalStateJson()).getBytes(StandardCharsets.UTF_8));
        Optional<DesiredShadowRequest> existing = desired.findByIdempotency(principal.accountId(), idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().matches(deviceId, command.expectedVersion(), requestDigest)) {
                throw new DesiredShadowIdempotencyConflictException();
            }
            return existing.orElseThrow();
        }

        TenantId tenant = currentTenant();
        var now = clock.instant();
        UUID requestId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        DesiredShadowRequest request = DesiredShadowRequest.create(requestId, tenant.value(),
                details.device().organizationId(), deviceId, principal.accountId(), idempotencyKey,
                command.expectedVersion(), state.canonicalStateJson(), requestDigest, commandId,
                now, now.plus(command.ttl()));
        byte[] payload = commandPayload(request, state.modelVersion());
        if (payload.length > 65_536) throw new IllegalArgumentException("Desired shadow command exceeds 64 KiB");
        String internalIdempotency = "desired:" + requestId;
        DeviceCommandRequest commandRequest = DeviceCommandRequest.create(commandId, tenant.value(),
                details.device().organizationId(), deviceId, principal.accountId(), internalIdempotency,
                "shadow.desired.patch", sha256(payload), now, request.expiresAt());
        DeviceCommandRequested event = new DeviceCommandRequested(1, UUID.randomUUID(), commandId,
                tenant.value(), deviceId, principal.accountId(), internalIdempotency,
                "shadow.desired.patch", payload, now, request.expiresAt());
        return desired.submit(request, commandRequest, event);
    }

    public DesiredShadowRequest get(AuthenticatedPrincipal principal, UUID deviceId) {
        requireAccess(principal, deviceId, "device:view", ResourceAction.VIEW);
        return desired.findCurrent(deviceId)
                .orElseThrow(() -> new DeviceMonitoringNotFoundException("Device has no desired shadow"));
    }

    private DeviceRepository.DeviceDetails requireAccess(AuthenticatedPrincipal principal, UUID deviceId,
            String permission, ResourceAction action) {
        TenantId tenant = currentTenant();
        DeviceRepository.DeviceDetails details = devices.findDetails(deviceId)
                .orElseThrow(() -> new DeviceMonitoringNotFoundException("Unknown device"));
        if (!details.device().tenantId().equals(tenant.value())) throw new DeviceMonitoringAccessDeniedException();
        if (!authorization.isAllowed(principal, permission, action,
                new ProtectedResource(tenant, "device", deviceId, Optional.of(details.organizationPath())))) {
            throw new DeviceMonitoringAccessDeniedException();
        }
        return details;
    }

    private static void validate(RequestDesiredState command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key is required and must be at most 128 characters");
        }
        if (command.expectedVersion() < 0) throw new IllegalArgumentException("If-Match version must be non-negative");
        if (command.stateJson() == null || command.stateJson().isBlank()) {
            throw new IllegalArgumentException("Desired state is required");
        }
        if (command.ttl() == null || command.ttl().isZero() || command.ttl().isNegative()
                || command.ttl().compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("Desired state TTL must be between 1 ms and 10 minutes");
        }
    }

    private static byte[] commandPayload(DesiredShadowRequest request, long modelVersion) {
        String json = "{\"schemaVersion\":1,\"desiredRequestId\":\"" + request.requestId()
                + "\",\"expectedDesiredVersion\":" + request.expectedVersion()
                + ",\"desiredVersion\":" + request.desiredVersion()
                + ",\"modelVersion\":" + modelVersion
                + ",\"state\":" + request.desiredStateJson() + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Desired shadow requires tenant scope"));
    }

    public record RequestDesiredState(String idempotencyKey, long expectedVersion,
            String stateJson, Duration ttl) { }
}
