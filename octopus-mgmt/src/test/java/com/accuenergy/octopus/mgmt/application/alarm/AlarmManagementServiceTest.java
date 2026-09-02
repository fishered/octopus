package com.accuenergy.octopus.mgmt.application.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmIncident;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlarmManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID organizationId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID meterId = UUID.randomUUID();
    private final UUID parameterId = UUID.randomUUID();
    private final MemoryAlarmRepository repository = new MemoryAlarmRepository();
    private final AlarmManagementService service = new AlarmManagementService(repository,
            new AuthorizationPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsTenantScopedRuleForAuthorizedDevice() throws Exception {
        repository.meter = new AlarmRepository.MeterContext(tenant.value(), organizationId, deviceId,
                parameterId, "/root/site-a");

        AlarmRule rule = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.createRule(
                operator("alarm:configure", "/root"), new AlarmManagementService.CreateRule(meterId,
                        parameterId, "voltage-high", "Voltage high", AlarmRule.ValueSelector.RAW,
                        AlarmRule.Comparison.GREATER_THAN, new BigDecimal("250"), new BigDecimal("245"),
                        AlarmRule.Severity.CRITICAL)));

        assertEquals(tenant.value(), rule.tenantId());
        assertEquals(deviceId, rule.deviceId());
        assertEquals(rule, repository.rule);
    }

    @Test
    void deniesOperatorOutsideOrganizationAndResourceScope() {
        repository.meter = new AlarmRepository.MeterContext(tenant.value(), organizationId, deviceId,
                parameterId, "/other/site-a");
        var command = new AlarmManagementService.CreateRule(meterId, parameterId, "voltage-high",
                "Voltage high", AlarmRule.ValueSelector.RAW, AlarmRule.Comparison.GREATER_THAN,
                new BigDecimal("250"), null, AlarmRule.Severity.WARNING);

        assertThrows(AlarmAccessDeniedException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.createRule(operator("alarm:configure", "/root"), command)));
    }

    private AuthenticatedPrincipal operator(String permission, String path) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permission),
                Set.of(path), Set.of());
    }

    private static final class MemoryAlarmRepository implements AlarmRepository {
        private MeterContext meter;
        private AlarmRule rule;
        public boolean ruleCodeExists(String code) { return false; }
        public Optional<MeterContext> findMeterContext(UUID meterId) { return Optional.ofNullable(meter); }
        public Optional<DeviceContext> findDeviceContext(UUID deviceId) { return Optional.empty(); }
        public void insertRule(AlarmRule rule) { this.rule = rule; }
        public Optional<RuleDetails> findRule(UUID ruleId) { return Optional.empty(); }
        public Optional<IncidentDetails> findIncident(UUID incidentId) { return Optional.empty(); }
        public List<AlarmIncident> findDeviceIncidents(UUID deviceId, Set<AlarmIncident.State> states, int limit) {
            return List.of();
        }
        public void updateIncident(AlarmIncident incident) { }
        public void evaluate(NormalizedTelemetry telemetry) { }
    }
}
