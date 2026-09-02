package com.accuenergy.octopus.control.infrastructure.mqtt;

import com.accuenergy.octopus.api.control.DeviceCommandResult;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.control.application.ApplyDeviceCommandResultService;
import com.accuenergy.octopus.control.application.DevicePresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "octopus.mqtt", name = "enabled", havingValue = "true")
public final class PahoCommandResultHandler {
    private static final Logger log = LoggerFactory.getLogger(PahoCommandResultHandler.class);
    private final ObjectMapper objectMapper;
    private final ApplyDeviceCommandResultService results;
    private final DevicePresenceService presence;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PahoCommandResultHandler(ObjectMapper objectMapper, ApplyDeviceCommandResultService results,
            DevicePresenceService presence, Clock clock,
            @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.results = results;
        this.presence = presence;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public boolean supports(String topic) { return isCommandResultTopic(topic); }

    static boolean isCommandResultTopic(String topic) {
        if (topic == null) return false;
        String[] parts = topic.split("/", -1);
        return parts.length == 6 && "octopus".equals(parts[0]) && "devices".equals(parts[2])
                && "command-results".equals(parts[4]);
    }

    public void handle(String topic, byte[] payload) throws Exception {
        TopicAddress address = parse(topic).orElseThrow(() -> new IllegalArgumentException("Invalid command result topic"));
        DeviceCommandResult result = objectMapper.readValue(payload, DeviceCommandResult.class);
        if (!address.tenantId().equals(result.tenantId()) || !address.deviceId().equals(result.deviceId())
                || !address.commandId().equals(result.commandId())) {
            throw new IllegalArgumentException("Command result payload does not match MQTT topic");
        }
        TenantContext.run(new TenantScope.Scoped(new TenantId(result.tenantId())), () ->
                transactions.executeWithoutResult(ignored -> results.apply(result)));
        try {
            presence.observe(result.tenantId(), result.deviceId(), clock.instant());
        } catch (RuntimeException unavailable) {
            log.warn("Unable to refresh presence after durable command result for device {}",
                    result.deviceId(), unavailable);
        }
    }

    private static Optional<TopicAddress> parse(String topic) {
        if (!isCommandResultTopic(topic)) return Optional.empty();
        String[] parts = topic.split("/", -1);
        try {
            return Optional.of(new TopicAddress(UUID.fromString(parts[1]), UUID.fromString(parts[3]),
                    UUID.fromString(parts[5])));
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    private record TopicAddress(UUID tenantId, UUID deviceId, UUID commandId) { }
}
