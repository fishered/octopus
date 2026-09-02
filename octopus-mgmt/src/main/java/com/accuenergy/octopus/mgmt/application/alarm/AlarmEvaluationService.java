package com.accuenergy.octopus.mgmt.application.alarm;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.common.tenant.TenantContext;

public final class AlarmEvaluationService {
    private final AlarmRepository alarms;

    public AlarmEvaluationService(AlarmRepository alarms) {
        this.alarms = alarms;
    }

    public void evaluate(NormalizedTelemetry telemetry) {
        var tenantId = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Alarm evaluation requires tenant scope"));
        if (!tenantId.value().equals(telemetry.tenantId())) {
            throw new IllegalArgumentException("Telemetry tenant does not match evaluation scope");
        }
        alarms.evaluate(telemetry);
    }
}
