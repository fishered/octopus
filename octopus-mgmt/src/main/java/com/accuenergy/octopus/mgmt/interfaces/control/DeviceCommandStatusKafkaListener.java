package com.accuenergy.octopus.mgmt.interfaces.control;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.control.DeviceCommandStatusProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public final class DeviceCommandStatusKafkaListener {
    private final ObjectMapper objectMapper;
    private final DeviceCommandStatusProjectionService projection;

    public DeviceCommandStatusKafkaListener(ObjectMapper objectMapper,
            DeviceCommandStatusProjectionService projection) {
        this.objectMapper = objectMapper;
        this.projection = projection;
    }

    @KafkaListener(topics = "${octopus.kafka.command-status-topic:octopus.local.command.status.v1}",
            groupId = "${octopus.kafka.command-status-group:octopus-mgmt-command-status-v1}")
    public void consume(byte[] payload, Acknowledgment acknowledgment) throws Exception {
        DeviceCommandStatusChanged event = objectMapper.readValue(payload, DeviceCommandStatusChanged.class);
        TenantContext.run(new TenantScope.Scoped(new TenantId(event.tenantId())), () -> projection.apply(event));
        acknowledgment.acknowledge();
    }
}
