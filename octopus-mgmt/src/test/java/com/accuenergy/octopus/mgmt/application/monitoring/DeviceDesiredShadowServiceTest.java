package com.accuenergy.octopus.mgmt.application.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import com.accuenergy.octopus.mgmt.domain.monitoring.DesiredShadowRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceDesiredShadowServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID deviceId = UUID.randomUUID();
    private final Device device = Device.register(deviceId, tenant.value(), UUID.randomUUID(), null,
            UUID.randomUUID(), UUID.randomUUID(), 8, "device-1", "Device 1", NOW);
    private final MemoryDesiredRepository repository = new MemoryDesiredRepository();
    private final DeviceDesiredShadowService service = new DeviceDesiredShadowService(new MemoryDevices(),
            (id, json) -> new DeviceDesiredStateValidator.ValidatedState(8, json), repository,
            new AuthorizationPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsReliableShadowPatchCommand() throws Exception {
        DesiredShadowRequest request = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.request(
                operator("/root"), deviceId, command("desired-1", 0, "{\"relay\":true}")));

        assertEquals(1, request.desiredVersion());
        assertEquals("shadow.desired.patch", repository.command.operation());
        assertEquals(request.commandId(), repository.event.commandId());
        assertEquals(8, repository.commandPayloadModelVersion());
    }

    @Test
    void sameIdempotencyRequestReturnsExistingIntent() throws Exception {
        AuthenticatedPrincipal principal = operator("/root");
        DesiredShadowRequest first = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.request(principal, deviceId, command("desired-1", 0, "{\"relay\":true}")));
        DesiredShadowRequest second = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.request(principal, deviceId, command("desired-1", 0, "{\"relay\":true}")));

        assertSame(first, second);
        assertEquals(1, repository.submits);
    }

    @Test
    void deniesOutsideOrganizationScope() {
        assertThrows(DeviceMonitoringAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant), () -> service.request(operator("/other"),
                        deviceId, command("desired-1", 0, "{\"relay\":true}"))));
    }

    private DeviceDesiredShadowService.RequestDesiredState command(String key, long version, String json) {
        return new DeviceDesiredShadowService.RequestDesiredState(key, version, json, Duration.ofSeconds(30));
    }

    private AuthenticatedPrincipal operator(String path) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of("device:operate"),
                Set.of(path), Set.of());
    }

    private final class MemoryDevices implements DeviceRepository {
        @Override public boolean existsByCode(String code) { return true; }
        @Override public void insert(Device value) { }
        @Override public Optional<DeviceDetails> findDetails(UUID id) {
            return deviceId.equals(id) ? Optional.of(new DeviceDetails(device, "/root/site-a")) : Optional.empty();
        }
        @Override public Optional<String> findOrganizationPath(UUID id) { return Optional.of("/root/site-a"); }
    }

    private static final class MemoryDesiredRepository implements DeviceDesiredShadowRepository {
        private DesiredShadowRequest desired;
        private DeviceCommandRequest command;
        private DeviceCommandRequested event;
        private int submits;
        @Override public Optional<DesiredShadowRequest> findByIdempotency(UUID requestedBy, String key) {
            return desired != null && desired.requestedBy().equals(requestedBy)
                    && desired.idempotencyKey().equals(key) ? Optional.of(desired) : Optional.empty();
        }
        @Override public Optional<DesiredShadowRequest> findCurrent(UUID deviceId) { return Optional.ofNullable(desired); }
        @Override public DesiredShadowRequest submit(DesiredShadowRequest desired, DeviceCommandRequest command,
                DeviceCommandRequested event) {
            this.desired = desired; this.command = command; this.event = event; submits++; return desired;
        }
        @Override public void applyCommandStatus(DeviceCommandStatusChanged event) { }
        @Override public void reconcile(DeviceShadowReported event) { }
        private long commandPayloadModelVersion() {
            String payload = new String(event.payload(), java.nio.charset.StandardCharsets.UTF_8);
            return payload.contains("\"modelVersion\":8") ? 8 : -1;
        }
    }
}
