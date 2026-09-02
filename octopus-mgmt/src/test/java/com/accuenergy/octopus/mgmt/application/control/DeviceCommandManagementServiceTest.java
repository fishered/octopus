package com.accuenergy.octopus.mgmt.application.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCommandManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID organizationId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final MemoryDeviceRepository devices = new MemoryDeviceRepository();
    private final MemoryCommandRepository commands = new MemoryCommandRepository();
    private final DeviceCommandManagementService service = new DeviceCommandManagementService(devices, commands,
            new AuthorizationPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void authorizedRequestCreatesProjectionAndOutboxEvent() throws Exception {
        byte[] payload = {1, 2, 3};

        DeviceCommandRequest request = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.request(
                operator("/root"), deviceId, new DeviceCommandManagementService.RequestCommand(
                        "request-1", "reboot", payload, Duration.ofSeconds(30))));

        payload[0] = 9;
        assertEquals(tenant.value(), request.tenantId());
        assertEquals(DeviceCommandStatusChanged.Status.ACCEPTED, request.status());
        assertEquals(request.commandId(), commands.event.commandId());
        assertArrayEquals(new byte[]{1, 2, 3}, commands.event.payload());
        assertEquals(NOW.plusSeconds(30), commands.event.expiresAt());
    }

    @Test
    void repeatedIdempotencyKeyReturnsSameRequestWithoutSecondInsert() throws Exception {
        AuthenticatedPrincipal principal = operator("/root");
        var command = new DeviceCommandManagementService.RequestCommand("request-1", "reboot",
                new byte[]{1}, Duration.ofSeconds(30));

        DeviceCommandRequest first = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.request(principal, deviceId, command));
        DeviceCommandRequest second = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.request(principal, deviceId, command));

        assertSame(first, second);
        assertEquals(1, commands.inserts);
    }

    @Test
    void conflictingUseOfIdempotencyKeyIsRejected() throws Exception {
        AuthenticatedPrincipal principal = operator("/root");
        TenantContext.run(new TenantScope.Scoped(tenant), () -> service.request(principal, deviceId,
                new DeviceCommandManagementService.RequestCommand("request-1", "reboot",
                        new byte[]{1}, Duration.ofSeconds(30))));

        assertThrows(IdempotencyConflictException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.request(principal, deviceId, new DeviceCommandManagementService.RequestCommand(
                        "request-1", "reboot", new byte[]{2}, Duration.ofSeconds(30)))));
    }

    @Test
    void deniesOperatorOutsideOrganizationScope() {
        assertThrows(DeviceCommandAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant), () -> service.request(operator("/other"),
                        deviceId, new DeviceCommandManagementService.RequestCommand("request-1", "reboot",
                                new byte[]{1}, Duration.ofSeconds(30)))));
    }

    @Test
    void validatesPayloadAndTtlBoundaries() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant), () -> service.request(operator("/root"),
                        deviceId, new DeviceCommandManagementService.RequestCommand("request-1", "reboot",
                                new byte[65_537], Duration.ofSeconds(30)))));
        assertThrows(IllegalArgumentException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant), () -> service.request(operator("/root"),
                        deviceId, new DeviceCommandManagementService.RequestCommand("request-2", "reboot",
                                new byte[0], Duration.ofMinutes(11)))));
    }

    private AuthenticatedPrincipal operator(String organizationPath) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of("device:operate"),
                Set.of(organizationPath), Set.of());
    }

    private final class MemoryDeviceRepository implements DeviceRepository {
        private final Device device = Device.register(deviceId, tenant.value(), organizationId, null,
                UUID.randomUUID(), UUID.randomUUID(), 1, "device-1", "Device 1", NOW);
        @Override public boolean existsByCode(String code) { return true; }
        @Override public void insert(Device device) { }
        @Override public Optional<DeviceDetails> findDetails(UUID requestedDeviceId) {
            return device.id().equals(requestedDeviceId)
                    ? Optional.of(new DeviceDetails(device, "/root/site-a")) : Optional.empty();
        }
        @Override public Optional<String> findOrganizationPath(UUID requestedOrganizationId) {
            return organizationId.equals(requestedOrganizationId) ? Optional.of("/root/site-a") : Optional.empty();
        }
    }

    private static final class MemoryCommandRepository implements DeviceCommandRequestRepository {
        private DeviceCommandRequest request;
        private DeviceCommandRequested event;
        private int inserts;
        @Override public Optional<DeviceCommandRequest> findByIdempotency(UUID requestedBy, String idempotencyKey) {
            return request != null && request.requestedBy().equals(requestedBy)
                    && request.idempotencyKey().equals(idempotencyKey) ? Optional.of(request) : Optional.empty();
        }
        @Override public Optional<CommandDetails> find(UUID commandId) {
            return request != null && request.commandId().equals(commandId)
                    ? Optional.of(new CommandDetails(request, "/root/site-a")) : Optional.empty();
        }
        @Override public void insert(DeviceCommandRequest request, DeviceCommandRequested event) {
            this.request = request;
            this.event = event;
            inserts++;
        }
        @Override public void applyStatus(DeviceCommandStatusChanged event) {
            if (request != null) request.apply(event);
        }
    }
}
