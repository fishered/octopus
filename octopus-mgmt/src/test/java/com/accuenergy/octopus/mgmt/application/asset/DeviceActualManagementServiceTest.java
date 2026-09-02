package com.accuenergy.octopus.mgmt.application.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceActual;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceActualManagementServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID deviceId = UUID.randomUUID();
    private final MemoryDeviceActualRepository repository = new MemoryDeviceActualRepository();
    private final DeviceActualManagementService service = new DeviceActualManagementService(repository,
            new AuthorizationPolicy(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void commissionsHardwareAgainstAuthorizedLogicalDevice() {
        repository.device = new DeviceActualRepository.DeviceContext(tenant.value(), "/root/site-a");
        var command = new DeviceActualManagementService.CommissionDeviceActual(deviceId, "HW-0001",
                "Octopus", "1.0.0", null);
        TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.commission(operator("device:configure"), command));
        assertEquals("HW-0001", repository.saved.hardwareSerial());
        assertEquals(DeviceActual.ConnectivityStatus.NEVER_SEEN, repository.saved.connectivityStatus());
    }

    @Test
    void rejectsCertificateAlreadyBoundToOtherHardware() {
        repository.device = new DeviceActualRepository.DeviceContext(tenant.value(), "/root/site-a");
        repository.certificateInUse = true;
        var command = new DeviceActualManagementService.CommissionDeviceActual(deviceId, "HW-0002",
                null, null, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.commission(operator("device:configure"), command)));
    }

    private AuthenticatedPrincipal operator(String permission) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permission),
                Set.of("/root"), Set.of());
    }

    private static final class MemoryDeviceActualRepository implements DeviceActualRepository {
        private DeviceContext device;
        private boolean certificateInUse;
        private DeviceActual saved;
        public Optional<DeviceContext> findDeviceContext(UUID id) { return Optional.ofNullable(device); }
        public boolean hardwareSerialExists(String serial) { return false; }
        public boolean deviceAlreadyCommissioned(UUID id) { return saved != null; }
        public boolean certificateInUse(UUID id, UUID excluding) { return certificateInUse; }
        public void insert(DeviceActual actual) { saved = actual; }
        public void update(DeviceActual actual) { saved = actual; }
        public Optional<DeviceActualDetails> findDetailsByDeviceId(UUID id) {
            return saved == null ? Optional.empty() : Optional.of(new DeviceActualDetails(saved, "/root/site-a"));
        }
    }
}
