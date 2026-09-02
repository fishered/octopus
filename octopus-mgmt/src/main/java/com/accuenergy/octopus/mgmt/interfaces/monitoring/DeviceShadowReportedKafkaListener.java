package com.accuenergy.octopus.mgmt.interfaces.monitoring;

import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceShadowProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public final class DeviceShadowReportedKafkaListener {
    private final ObjectMapper objectMapper;
    private final DeviceShadowProjectionService projection;

    public DeviceShadowReportedKafkaListener(ObjectMapper objectMapper, DeviceShadowProjectionService projection) {
        this.objectMapper = objectMapper;
        this.projection = projection;
    }

    @KafkaListener(topics = "${octopus.kafka.shadow-reported-topic:octopus.local.shadow.reported.v1}",
            groupId = "${octopus.kafka.shadow-projection-group:octopus-mgmt-shadow-v1}")
    public void consume(byte[] payload, Acknowledgment acknowledgment) throws Exception {
        DeviceShadowReported event = objectMapper.readValue(payload, DeviceShadowReported.class);
        TenantContext.run(new TenantScope.Scoped(new TenantId(event.tenantId())), () -> projection.apply(event));
        acknowledgment.acknowledge();
    }
}
