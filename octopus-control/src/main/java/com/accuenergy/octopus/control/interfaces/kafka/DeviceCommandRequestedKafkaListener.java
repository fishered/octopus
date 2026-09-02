package com.accuenergy.octopus.control.interfaces.kafka;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.control.application.DispatchDeviceCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "octopus.command-dispatch", name = "enabled", havingValue = "true")
public final class DeviceCommandRequestedKafkaListener {
    private final ObjectMapper objectMapper;
    private final DispatchDeviceCommandService dispatcher;
    private final TransactionTemplate transactions;

    public DeviceCommandRequestedKafkaListener(ObjectMapper objectMapper, DispatchDeviceCommandService dispatcher,
            @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @KafkaListener(topics = "${octopus.kafka.command-requested-topic:octopus.local.command.requested.v1}",
            groupId = "${octopus.kafka.command-dispatch-group:octopus-control-command-v1}")
    public void consume(byte[] payload, Acknowledgment acknowledgment) throws Exception {
        DeviceCommandRequested request = objectMapper.readValue(payload, DeviceCommandRequested.class);
        TenantContext.run(new TenantScope.Scoped(new TenantId(request.tenantId())), () ->
                transactions.executeWithoutResult(ignored -> dispatcher.dispatch(request)));
        acknowledgment.acknowledge();
    }
}
