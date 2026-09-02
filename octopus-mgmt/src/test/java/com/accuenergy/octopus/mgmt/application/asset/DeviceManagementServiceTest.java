package com.accuenergy.octopus.mgmt.application.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.security.ResourceGrant;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceManagementServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID organization = UUID.randomUUID();
    private final MemoryRepository repository = new MemoryRepository();
    private final DeviceManagementService service = new DeviceManagementService(repository,
            new AuthorizationPolicy(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void operatorCanRegisterInsideOrganizationScope() {
        repository.organizationPath = "/root/east/site-a";
        var principal = operator(Set.of("device:create"), Set.of("/root/east"), Set.of());
        DeviceManagementService.RegisterDevice command = new DeviceManagementService.RegisterDevice(
                organization, null, UUID.randomUUID(), UUID.randomUUID(), 1, "device-1", "Device 1");
        TenantContext.run(new TenantScope.Scoped(tenant), () -> service.register(principal, command));
        assertEquals("device-1", repository.saved.code());
        assertEquals(tenant.value(), repository.saved.tenantId());
    }

    @Test
    void operatorCannotRegisterOutsideOrganizationScope() {
        repository.organizationPath = "/root/west";
        var principal = operator(Set.of("device:create"), Set.of("/root/east"), Set.of());
        var command = new DeviceManagementService.RegisterDevice(organization, null, UUID.randomUUID(),
                UUID.randomUUID(), 1, "device-2", "Device 2");
        assertThrows(DeviceAccessDeniedException.class, () -> TenantContext.run(
                new TenantScope.Scoped(tenant), () -> service.register(principal, command)));
    }

    @Test
    void explicitGrantAllowsViewingDeviceOutsideOrganizationScope() {
        UUID deviceId = UUID.randomUUID();
        repository.details = new DeviceRepository.DeviceDetails(Device.register(deviceId, tenant.value(), organization,
                null, UUID.randomUUID(), UUID.randomUUID(), 1, "device-3", "Device 3", Instant.now()), "/root/west");
        var principal = operator(Set.of("device:view"), Set.of("/root/east"),
                Set.of(new ResourceGrant("device", deviceId, ResourceAction.VIEW)));
        assertEquals(deviceId, service.get(principal, deviceId).id());
    }

    private AuthenticatedPrincipal operator(Set<String> permissions, Set<String> paths, Set<ResourceGrant> grants) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), permissions, paths, grants);
    }

    private static final class MemoryRepository implements DeviceRepository {
        private String organizationPath;
        private Device saved;
        private DeviceDetails details;
        public boolean existsByCode(String code) { return false; }
        public void insert(Device device) { saved = device; }
        public Optional<DeviceDetails> findDetails(UUID id) { return Optional.ofNullable(details); }
        public Optional<String> findOrganizationPath(UUID id) { return Optional.ofNullable(organizationPath); }
    }
}
