package com.accuenergy.octopus.mgmt.application.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.control.DevicePresenceSnapshot;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import com.accuenergy.octopus.mgmt.domain.monitoring.DeviceShadow;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceMonitoringServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID deviceId = UUID.randomUUID();
    private final Device device = Device.register(deviceId, tenant.value(), UUID.randomUUID(), null,
            UUID.randomUUID(), UUID.randomUUID(), 1, "device-1", "Device 1", NOW);
    private final DeviceShadow shadow = DeviceShadow.from(new DeviceShadowReported(1, UUID.randomUUID(),
            tenant.value(), deviceId, 1, "{\"relay\":true}", NOW, NOW));
    private final DeviceMonitoringService service = new DeviceMonitoringService(new MemoryDevices(),
            (tenantId, requestedDeviceId) -> new DevicePresenceSnapshot(tenantId, requestedDeviceId,
                    DevicePresenceSnapshot.Status.ONLINE, NOW, NOW.plusSeconds(120)),
            new MemoryShadows(), new AuthorizationPolicy());

    @Test
    void returnsPresenceAndShadowWithinOrganizationScope() throws Exception {
        DevicePresenceSnapshot presence = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.presence(operator("/root"), deviceId));
        DeviceShadow result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.shadow(operator("/root"), deviceId));

        assertEquals(DevicePresenceSnapshot.Status.ONLINE, presence.status());
        assertEquals(shadow, result);
    }

    @Test
    void deniesOperatorOutsideOrganizationScope() {
        assertThrows(DeviceMonitoringAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.presence(operator("/other"), deviceId)));
    }

    private AuthenticatedPrincipal operator(String path) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of("device:view"),
                Set.of(path), Set.of());
    }

    private final class MemoryDevices implements DeviceRepository {
        @Override public boolean existsByCode(String code) { return true; }
        @Override public void insert(Device value) { }
        @Override public Optional<DeviceDetails> findDetails(UUID requestedId) {
            return deviceId.equals(requestedId) ? Optional.of(new DeviceDetails(device, "/root/site-a"))
                    : Optional.empty();
        }
        @Override public Optional<String> findOrganizationPath(UUID organizationId) { return Optional.of("/root/site-a"); }
    }

    private final class MemoryShadows implements DeviceShadowRepository {
        @Override public void apply(DeviceShadowReported event) { }
        @Override public Optional<DeviceShadow> find(UUID requestedId) {
            return deviceId.equals(requestedId) ? Optional.of(shadow) : Optional.empty();
        }
    }
}
