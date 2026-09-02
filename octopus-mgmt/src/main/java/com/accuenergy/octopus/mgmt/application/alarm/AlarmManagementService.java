package com.accuenergy.octopus.mgmt.application.alarm;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmIncident;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AlarmManagementService {
    private final AlarmRepository alarms;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public AlarmManagementService(AlarmRepository alarms, AuthorizationPolicy authorization, Clock clock) {
        this.alarms = alarms;
        this.authorization = authorization;
        this.clock = clock;
    }

    public AlarmRule createRule(AuthenticatedPrincipal principal, CreateRule command) {
        TenantId tenantId = currentTenant();
        AlarmRepository.MeterContext meter = alarms.findMeterContext(command.meterId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown active meter"));
        if (!tenantId.value().equals(meter.tenantId()) || !meter.parameterId().equals(command.parameterId())) {
            throw new AlarmAccessDeniedException();
        }
        requireAllowed(principal, "alarm:configure", ResourceAction.CONFIGURE, tenantId,
                meter.deviceId(), meter.organizationPath());
        if (alarms.ruleCodeExists(command.code())) {
            throw new IllegalArgumentException("Alarm rule code already exists");
        }
        AlarmRule rule = AlarmRule.create(UUID.randomUUID(), tenantId.value(), meter.organizationId(),
                meter.deviceId(), command.meterId(), meter.parameterId(), command.code(), command.displayName(),
                command.valueSelector(), command.comparison(), command.triggerThreshold(), command.clearThreshold(),
                command.severity(), clock.instant());
        alarms.insertRule(rule);
        return rule;
    }

    public AlarmRule getRule(AuthenticatedPrincipal principal, UUID ruleId) {
        AlarmRepository.RuleDetails details = alarms.findRule(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alarm rule"));
        requireAllowed(principal, "alarm:view", ResourceAction.VIEW, currentTenant(),
                details.rule().deviceId(), details.organizationPath());
        return details.rule();
    }

    public AlarmIncident getIncident(AuthenticatedPrincipal principal, UUID incidentId) {
        AlarmRepository.IncidentDetails details = alarms.findIncident(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alarm incident"));
        requireAllowed(principal, "alarm:view", ResourceAction.VIEW, currentTenant(),
                details.incident().deviceId(), details.organizationPath());
        return details.incident();
    }

    public List<AlarmIncident> listDeviceIncidents(AuthenticatedPrincipal principal, UUID deviceId,
            Set<AlarmIncident.State> states, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        AlarmRepository.DeviceContext device = alarms.findDeviceContext(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device"));
        TenantId tenantId = currentTenant();
        if (!tenantId.value().equals(device.tenantId())) throw new AlarmAccessDeniedException();
        requireAllowed(principal, "alarm:view", ResourceAction.VIEW, tenantId, deviceId,
                device.organizationPath());
        Set<AlarmIncident.State> requested = states == null || states.isEmpty()
                ? Set.of(AlarmIncident.State.OPEN, AlarmIncident.State.ACKNOWLEDGED) : Set.copyOf(states);
        return alarms.findDeviceIncidents(deviceId, requested, limit);
    }

    public AlarmIncident acknowledge(AuthenticatedPrincipal principal, UUID incidentId) {
        AlarmRepository.IncidentDetails details = alarms.findIncident(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alarm incident"));
        requireAllowed(principal, "alarm:acknowledge", ResourceAction.ACKNOWLEDGE, currentTenant(),
                details.incident().deviceId(), details.organizationPath());
        if (details.incident().acknowledge(principal.accountId(), clock.instant())) {
            alarms.updateIncident(details.incident());
        }
        return details.incident();
    }

    private void requireAllowed(AuthenticatedPrincipal principal, String permission, ResourceAction action,
            TenantId tenantId, UUID deviceId, String organizationPath) {
        if (!authorization.isAllowed(principal, permission, action,
                new ProtectedResource(tenantId, "device", deviceId, Optional.of(organizationPath)))) {
            throw new AlarmAccessDeniedException();
        }
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Alarm operations require tenant scope"));
    }

    public record CreateRule(UUID meterId, UUID parameterId, String code, String displayName,
                             AlarmRule.ValueSelector valueSelector, AlarmRule.Comparison comparison,
                             BigDecimal triggerThreshold, BigDecimal clearThreshold,
                             AlarmRule.Severity severity) { }
}
