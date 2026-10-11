package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceConnectorBinding;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Creates the logical device, physical instance, and active connector binding atomically. */
public final class DeviceOnboardingService implements DeviceOnboardingUseCase {
    private final DeviceManagementService devices;
    private final DeviceActualManagementService actuals;
    private final DeviceConnectorBindingManagementService bindings;
    private final DeviceOnboardingRequestRepository requests;
    private final Clock clock;

    public DeviceOnboardingService(DeviceManagementService devices,
            DeviceActualManagementService actuals,
            DeviceConnectorBindingManagementService bindings,
            DeviceOnboardingRequestRepository requests, Clock clock) {
        this.devices = devices;
        this.actuals = actuals;
        this.bindings = bindings;
        this.requests = requests;
        this.clock = clock;
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    @Override
    public OnboardingResult onboard(AuthenticatedPrincipal principal, OnboardDevice command) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");
        devices.requireOrganizationPermission(principal, command.organizationId(), "device:create");
        devices.requireOrganizationPermission(principal, command.organizationId(), "device:configure");
        String idempotencyKey = validateIdempotencyKey(command.idempotencyKey());
        String requestSha256 = requestSha256(command);
        Optional<DeviceOnboardingRequestRepository.RequestRecord> existing =
                requests.findForUpdate(principal.accountId(), idempotencyKey);
        if (existing.isPresent()) return replay(existing.orElseThrow(), requestSha256);

        UUID tenantId = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Device onboarding requires tenant scope")).value();
        UUID requestId = UUID.randomUUID();
        var now = clock.instant();
        if (!requests.insertClaim(requestId, tenantId, principal.accountId(), idempotencyKey,
                requestSha256, now)) {
            return replay(requests.findForUpdate(principal.accountId(), idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Onboarding idempotency claim disappeared")),
                    requestSha256);
        }

        var device = devices.register(principal, new DeviceManagementService.RegisterDevice(
                command.organizationId(), command.facilityId(), command.deviceTypeId(), command.thingModelId(),
                command.modelVersion(), command.code(), command.displayName()));
        var actual = actuals.commission(principal,
                new DeviceActualManagementService.CommissionDeviceActual(device.id(), command.hardwareSerial(),
                        command.manufacturer(), command.firmwareVersion(), command.certificateId()));
        var binding = bindings.upsert(principal, device.id(), command.pluginId(),
                command.codecId(), command.configRef(),
                DeviceConnectorBinding.Status.ACTIVE, 0L);
        var result = new OnboardingResult(device.id(), actual.id(), device.code(), actual.hardwareSerial(),
                binding.pluginId(), binding.codecId(), binding.version(), device.createdAt());
        requests.complete(requestId, result, clock.instant());
        return result;
    }

    private static OnboardingResult replay(DeviceOnboardingRequestRepository.RequestRecord existing,
            String requestSha256) {
        if (!existing.requestSha256().equals(requestSha256)) {
            throw new DeviceOnboardingIdempotencyConflictException();
        }
        return existing.result().orElseThrow(() ->
                new IllegalStateException("Onboarding idempotency claim is incomplete"));
    }

    private static String validateIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key is required and must be at most 128 characters");
        }
        return value.strip();
    }

    private static String requestSha256(OnboardDevice command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            put(digest, command.organizationId()); put(digest, command.facilityId());
            put(digest, command.deviceTypeId()); put(digest, command.thingModelId());
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(command.modelVersion()).array());
            put(digest, command.code()); put(digest, command.displayName());
            put(digest, command.hardwareSerial()); put(digest, command.manufacturer());
            put(digest, command.firmwareVersion()); put(digest, command.certificateId());
            put(digest, command.pluginId()); put(digest, command.codecId()); put(digest, command.configRef());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void put(MessageDigest digest, UUID value) {
        put(digest, value == null ? null : value.toString());
    }

    private static void put(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
