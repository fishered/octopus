package com.accuenergy.octopus.mgmt.application.alarm;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmIncident;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmRule;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AlarmRepository {
    boolean ruleCodeExists(String code);
    Optional<MeterContext> findMeterContext(UUID meterId);
    Optional<DeviceContext> findDeviceContext(UUID deviceId);
    void insertRule(AlarmRule rule);
    Optional<RuleDetails> findRule(UUID ruleId);
    Optional<IncidentDetails> findIncident(UUID incidentId);
    List<AlarmIncident> findDeviceIncidents(UUID deviceId, Set<AlarmIncident.State> states, int limit);
    void updateIncident(AlarmIncident incident);

    /** Atomically de-duplicates and applies one normalized event to every matching active rule. */
    void evaluate(NormalizedTelemetry telemetry);

    record MeterContext(UUID tenantId, UUID organizationId, UUID deviceId, UUID parameterId,
                        String organizationPath) { }
    record DeviceContext(UUID tenantId, String organizationPath) { }
    record RuleDetails(AlarmRule rule, String organizationPath) { }
    record IncidentDetails(AlarmIncident incident, String organizationPath) { }
}
