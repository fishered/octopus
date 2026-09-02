package com.accuenergy.octopus.mgmt.interfaces.alarm;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public final class NormalizedTelemetryKafkaListener {
    private final ObjectMapper objectMapper;
    private final AlarmEvaluationService alarms;

    public NormalizedTelemetryKafkaListener(ObjectMapper objectMapper, AlarmEvaluationService alarms) {
        this.objectMapper = objectMapper;
        this.alarms = alarms;
    }

    @KafkaListener(topics = "${octopus.kafka.telemetry-normalized-topic:octopus.local.telemetry.normalized.v1}",
            groupId = "${octopus.kafka.alarm-group:octopus-mgmt-alarm-v1}")
    public void consume(byte[] payload, Acknowledgment acknowledgment) throws Exception {
        NormalizedTelemetry telemetry = objectMapper.readValue(payload, NormalizedTelemetry.class);
        TenantContext.run(new TenantScope.Scoped(new TenantId(telemetry.tenantId())),
                () -> alarms.evaluate(telemetry));
        acknowledgment.acknowledge();
    }
}
