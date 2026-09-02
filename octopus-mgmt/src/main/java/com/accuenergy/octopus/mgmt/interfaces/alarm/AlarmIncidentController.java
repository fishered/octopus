package com.accuenergy.octopus.mgmt.interfaces.alarm;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmManagementService;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmIncident;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alarm-incidents")
public final class AlarmIncidentController {
    private final AlarmManagementService alarms;

    public AlarmIncidentController(AlarmManagementService alarms) {
        this.alarms = alarms;
    }

    @GetMapping("/{incidentId}")
    @PreAuthorize("hasAuthority('PERM_alarm:view') or hasAuthority('PERM_platform:all')")
    public AlarmIncidentResponse get(Authentication authentication, @PathVariable UUID incidentId) {
        return response(alarms.getIncident(principal(authentication), incidentId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_alarm:view') or hasAuthority('PERM_platform:all')")
    public List<AlarmIncidentResponse> list(Authentication authentication, @RequestParam UUID deviceId,
            @RequestParam(required = false) Set<AlarmIncident.State> state,
            @RequestParam(defaultValue = "50") int limit) {
        return alarms.listDeviceIncidents(principal(authentication), deviceId, state, limit).stream()
                .map(AlarmIncidentController::response).toList();
    }

    @PostMapping("/{incidentId}/acknowledge")
    @PreAuthorize("hasAuthority('PERM_alarm:acknowledge') or hasAuthority('PERM_platform:all')")
    public AlarmIncidentResponse acknowledge(Authentication authentication, @PathVariable UUID incidentId) {
        return response(alarms.acknowledge(principal(authentication), incidentId));
    }

    private static AlarmIncidentResponse response(AlarmIncident incident) {
        return new AlarmIncidentResponse(incident.id(), incident.ruleId(), incident.organizationId(),
                incident.deviceId(), incident.meterId(), incident.parameterId(), incident.severity(),
                incident.state(), incident.triggerValue(), incident.latestValue(), incident.occurrenceCount(),
                incident.openedAt(), incident.lastObservedAt(), incident.acknowledgedBy().orElse(null),
                incident.acknowledgedAt().orElse(null), incident.clearedAt().orElse(null), incident.version(),
                incident.updatedAt());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record AlarmIncidentResponse(UUID id, UUID ruleId, UUID organizationId, UUID deviceId,
            UUID meterId, UUID parameterId, AlarmRule.Severity severity, AlarmIncident.State state,
            BigDecimal triggerValue, BigDecimal latestValue, long occurrenceCount, Instant openedAt,
            Instant lastObservedAt, UUID acknowledgedBy, Instant acknowledgedAt, Instant clearedAt,
            long version, Instant updatedAt) { }
}
