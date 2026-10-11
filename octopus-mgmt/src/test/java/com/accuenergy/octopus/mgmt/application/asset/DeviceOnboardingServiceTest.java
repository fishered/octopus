package com.accuenergy.octopus.mgmt.application.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceActual;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceConnectorBinding;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceOnboardingServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID organizationId = UUID.randomUUID();
    private final UUID deviceTypeId = UUID.randomUUID();
    private final UUID thingModelId = UUID.randomUUID();
    private final MemoryDevices devices = new MemoryDevices();
    private final MemoryActuals actuals = new MemoryActuals(devices);
    private final MemoryBindings bindings = new MemoryBindings();
    private final MemoryOnboardingRequests requests = new MemoryOnboardingRequests();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    private final DeviceOnboardingService service = new DeviceOnboardingService(
            new DeviceManagementService(devices, new AuthorizationPolicy(), clock),
            new DeviceActualManagementService(actuals, new AuthorizationPolicy(), clock),
            new DeviceConnectorBindingManagementService(bindings, actuals, new AuthorizationPolicy(), clock),
            requests, clock);

    @Test
    void createsLogicalPhysicalAndConnectorRecordsInOneUseCase() throws Exception {
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant),
                Set.of("device:create", "device:configure"), Set.of("/root"), Set.of());
        var command = command("onboard-meter-001", "Main meter");

        DeviceOnboardingUseCase.OnboardingResult result = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.onboard(principal, command));

        assertNotNull(result.deviceId());
        assertEquals(result.deviceId(), actuals.saved.deviceId());
        assertEquals(result.deviceId(), bindings.saved.deviceId());
        assertEquals("mqtt-json", result.pluginId());
        assertEquals(DeviceConnectorBinding.Status.ACTIVE, bindings.saved.status());
        assertEquals(1, result.bindingVersion());
    }

    @Test
    void replaysCompletedResultWithoutCreatingDuplicateResources() throws Exception {
        var principal = principal();
        var command = command("retry-safe", "Main meter");

        DeviceOnboardingUseCase.OnboardingResult first = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.onboard(principal, command));
        DeviceOnboardingUseCase.OnboardingResult replay = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.onboard(principal, command));

        assertEquals(first, replay);
        assertEquals(1, devices.insertCount);
        assertEquals(1, actuals.insertCount);
        assertEquals(1, bindings.insertCount);
    }

    @Test
    void rejectsIdempotencyKeyReuseForDifferentPayload() throws Exception {
        var principal = principal();
        TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.onboard(principal, command("same-key", "Main meter")));

        assertThrows(DeviceOnboardingIdempotencyConflictException.class, () ->
                TenantContext.call(new TenantScope.Scoped(tenant),
                        () -> service.onboard(principal, command("same-key", "Different name"))));
        assertEquals(1, devices.insertCount);
    }

    @Test
    void rechecksOrganizationPermissionsBeforeReplayingAReceipt() throws Exception {
        UUID accountId = UUID.randomUUID();
        var allowed = principal(accountId, Set.of("device:create", "device:configure"));
        var command = command("permission-replay", "Main meter");
        TenantContext.call(new TenantScope.Scoped(tenant), () -> service.onboard(allowed, command));
        var revoked = principal(accountId, Set.of("device:create"));

        assertThrows(DeviceAccessDeniedException.class, () -> TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.onboard(revoked, command)));
    }

    private DeviceOnboardingUseCase.OnboardDevice command(String idempotencyKey, String displayName) {
        return new DeviceOnboardingUseCase.OnboardDevice(idempotencyKey, organizationId, null,
                deviceTypeId, thingModelId, 1, "meter-001", displayName, "HW-001",
                "Acme", "1.0.0", null, "mqtt-json", "json", "secret://mqtt/site-a");
    }

    private AuthenticatedPrincipal principal() {
        return principal(UUID.randomUUID(), Set.of("device:create", "device:configure"));
    }

    private AuthenticatedPrincipal principal(UUID accountId, Set<String> permissions) {
        return new AuthenticatedPrincipal(accountId, UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant),
                permissions, Set.of("/root"), Set.of());
    }

    private final class MemoryDevices implements DeviceRepository {
        private Device saved;
        private int insertCount;
        @Override public boolean existsByCode(String code) { return saved != null && saved.code().equals(code); }
        @Override public void insert(Device device) { saved = device; insertCount++; }
        @Override public Optional<DeviceDetails> findDetails(UUID deviceId) {
            return saved != null && saved.id().equals(deviceId)
                    ? Optional.of(new DeviceDetails(saved, "/root/site-a")) : Optional.empty();
        }
        @Override public Optional<String> findOrganizationPath(UUID id) {
            return organizationId.equals(id) ? Optional.of("/root/site-a") : Optional.empty();
        }
    }

    private static final class MemoryActuals implements DeviceActualRepository {
        private final MemoryDevices devices;
        private DeviceActual saved;
        private int insertCount;
        private MemoryActuals(MemoryDevices devices) { this.devices = devices; }
        @Override public Optional<DeviceContext> findDeviceContext(UUID deviceId) {
            return devices.saved != null && devices.saved.id().equals(deviceId)
                    ? Optional.of(new DeviceContext(devices.saved.tenantId(), "/root/site-a")) : Optional.empty();
        }
        @Override public boolean hardwareSerialExists(String serial) {
            return saved != null && saved.hardwareSerial().equals(serial);
        }
        @Override public boolean deviceAlreadyCommissioned(UUID deviceId) {
            return saved != null && saved.deviceId().equals(deviceId);
        }
        @Override public boolean certificateInUse(UUID certificateId, UUID excludingActualId) { return false; }
        @Override public void insert(DeviceActual actual) { saved = actual; insertCount++; }
        @Override public void update(DeviceActual actual) { saved = actual; }
        @Override public Optional<DeviceActualDetails> findDetailsByDeviceId(UUID deviceId) {
            return saved != null && saved.deviceId().equals(deviceId)
                    ? Optional.of(new DeviceActualDetails(saved, "/root/site-a")) : Optional.empty();
        }
    }

    private static final class MemoryBindings implements DeviceConnectorBindingRepository {
        private DeviceConnectorBinding saved;
        private int insertCount;
        @Override public Optional<DeviceConnectorBinding> find(UUID deviceId) {
            return saved != null && saved.deviceId().equals(deviceId) ? Optional.of(saved) : Optional.empty();
        }
        @Override public void insert(DeviceConnectorBinding binding) { saved = binding; insertCount++; }
        @Override public void update(DeviceConnectorBinding binding, long expectedVersion) { saved = binding; }
    }

    private static final class MemoryOnboardingRequests implements DeviceOnboardingRequestRepository {
        private RequestRecord saved;

        @Override public Optional<RequestRecord> findForUpdate(UUID requestedBy, String idempotencyKey) {
            return Optional.ofNullable(saved);
        }

        @Override public boolean insertClaim(UUID requestId, UUID tenantId, UUID requestedBy,
                String idempotencyKey, String requestSha256, Instant requestedAt) {
            if (saved != null) return false;
            saved = new RequestRecord(requestId, requestSha256, Optional.empty());
            return true;
        }

        @Override public void complete(UUID requestId, DeviceOnboardingUseCase.OnboardingResult result,
                Instant completedAt) {
            saved = new RequestRecord(requestId, saved.requestSha256(), Optional.of(result));
        }
    }
}
