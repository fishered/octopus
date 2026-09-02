package com.accuenergy.octopus.control.infrastructure.persistence.command;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.control.application.port.DeviceCommandRepository;
import com.accuenergy.octopus.control.domain.command.DeviceCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceCommandRepository implements DeviceCommandRepository {
    private final DeviceCommandMapper mapper;
    private final ObjectMapper objectMapper;
    private final String statusTopic;

    public MybatisDeviceCommandRepository(DeviceCommandMapper mapper, ObjectMapper objectMapper,
            @Value("${octopus.kafka.command-status-topic:octopus.local.command.status.v1}") String statusTopic) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.statusTopic = statusTopic;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceCommand> find(UUID tenantId, UUID commandId) {
        return Optional.ofNullable(mapper.find(tenantId, commandId)).map(MybatisDeviceCommandRepository::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(DeviceCommand command, DeviceCommandStatusChanged statusEvent) {
        requireMatches(command, statusEvent);
        if (mapper.insertCommand(toEntity(command)) != 1) throw new IllegalStateException("Unable to insert command");
        append(statusEvent);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void update(DeviceCommand command, DeviceCommandStatusChanged statusEvent) {
        requireMatches(command, statusEvent);
        if (mapper.updateCommand(toEntity(command), command.version() - 1) != 1) {
            throw new IllegalStateException("Device command was concurrently modified");
        }
        append(statusEvent);
    }

    private void append(DeviceCommandStatusChanged event) {
        if (mapper.insertStatusOutbox(event.eventId(), event.tenantId(), statusTopic, event.orderingKey(),
                serialize(event), event.occurredAt()) != 1) {
            throw new IllegalStateException("Unable to append command status event");
        }
    }

    private static void requireMatches(DeviceCommand command, DeviceCommandStatusChanged event) {
        if (!command.commandId().equals(event.commandId()) || !command.tenantId().equals(event.tenantId())
                || !command.deviceId().equals(event.deviceId())) {
            throw new IllegalArgumentException("Command status does not match aggregate");
        }
    }

    private String serialize(DeviceCommandStatusChanged event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize command status", failure);
        }
    }

    private static DeviceCommand toDomain(DeviceCommandEntity row) {
        return DeviceCommand.restore(row.getCommandId(), row.getTenantId(), row.getDeviceId(), row.getRequestedBy(),
                row.getOperation(), row.getPayload(), row.getRequestedAt(), row.getExpiresAt(),
                DeviceCommand.Status.valueOf(row.getStatus()), row.getDispatchedAt(), row.getAcknowledgedAt(),
                row.getCompletedAt(), row.getFailureCode(), row.getVersion());
    }

    private static DeviceCommandEntity toEntity(DeviceCommand command) {
        DeviceCommandEntity entity = new DeviceCommandEntity();
        entity.setCommandId(command.commandId()); entity.setTenantId(command.tenantId());
        entity.setDeviceId(command.deviceId()); entity.setRequestedBy(command.requestedBy());
        entity.setOperation(command.operation()); entity.setPayload(command.payload());
        entity.setStatus(command.status().name()); entity.setFailureCode(command.failureCode().orElse(null));
        entity.setRequestedAt(command.createdAt()); entity.setExpiresAt(command.expiresAt());
        entity.setDispatchedAt(command.dispatchedAt().orElse(null));
        entity.setAcknowledgedAt(command.acknowledgedAt().orElse(null));
        entity.setCompletedAt(command.completedAt().orElse(null)); entity.setVersion(command.version());
        return entity;
    }
}
