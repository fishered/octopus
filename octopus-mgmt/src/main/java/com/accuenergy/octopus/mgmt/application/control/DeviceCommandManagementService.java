package com.accuenergy.octopus.mgmt.application.control;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public final class DeviceCommandManagementService {
    private static final int MAX_PAYLOAD_BYTES = 65_536;
    private static final Duration MAX_TTL = Duration.ofMinutes(10);
    private final DeviceRepository devices;
    private final DeviceCommandRequestRepository commands;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public DeviceCommandManagementService(DeviceRepository devices, DeviceCommandRequestRepository commands,
            AuthorizationPolicy authorization, Clock clock) {
        this.devices = devices;
        this.commands = commands;
        this.authorization = authorization;
        this.clock = clock;
    }

    public DeviceCommandRequest request(AuthenticatedPrincipal principal, UUID deviceId, RequestCommand command) {
        TenantId tenantId = currentTenant();
        DeviceRepository.DeviceDetails details = devices.findDetails(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device"));
        if (!details.device().tenantId().equals(tenantId.value())) throw new DeviceCommandAccessDeniedException();
        if (!authorization.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                new ProtectedResource(tenantId, "device", deviceId, Optional.of(details.organizationPath())))) {
            throw new DeviceCommandAccessDeniedException();
        }
        validate(command);
        String operation = command.operation().strip();
        String idempotencyKey = command.idempotencyKey().strip();
        String digest = sha256(command.payload());
        Optional<DeviceCommandRequest> existing = commands.findByIdempotency(principal.accountId(), idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().matches(deviceId, operation, digest)) throw new IdempotencyConflictException();
            return existing.orElseThrow();
        }

        var now = clock.instant();
        DeviceCommandRequest request = DeviceCommandRequest.create(UUID.randomUUID(), tenantId.value(),
                details.device().organizationId(), deviceId, principal.accountId(), idempotencyKey, operation,
                digest, now, now.plus(command.ttl()));
        DeviceCommandRequested event = new DeviceCommandRequested(1, UUID.randomUUID(), request.commandId(),
                tenantId.value(), deviceId, principal.accountId(), idempotencyKey, operation, command.payload(),
                now, request.expiresAt());
        commands.insert(request, event);
        return request;
    }

    public DeviceCommandRequest get(AuthenticatedPrincipal principal, UUID commandId) {
        DeviceCommandRequestRepository.CommandDetails details = commands.find(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device command"));
        DeviceCommandRequest request = details.request();
        if (!authorization.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                new ProtectedResource(currentTenant(), "device", request.deviceId(),
                        Optional.of(details.organizationPath())))) {
            throw new DeviceCommandAccessDeniedException();
        }
        return request;
    }

    private static void validate(RequestCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key is required and must be at most 128 characters");
        }
        if (command.operation() == null || !command.operation().matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("operation is invalid");
        }
        if (command.payload() == null || command.payload().length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Command payload exceeds 64 KiB");
        }
        if (command.ttl() == null || command.ttl().isZero() || command.ttl().isNegative()
                || command.ttl().compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("Command TTL must be between 1 ms and 10 minutes");
        }
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Device commands require tenant scope"));
    }

    public record RequestCommand(String idempotencyKey, String operation, byte[] payload, Duration ttl) {
        public RequestCommand { payload = payload == null ? null : payload.clone(); }
        @Override public byte[] payload() { return payload == null ? null : payload.clone(); }
    }
}
