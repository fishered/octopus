package com.accuenergy.octopus.mgmt.infrastructure.persistence.control;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.mgmt.application.control.DeviceCommandRequestRepository;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceCommandRequestRepository implements DeviceCommandRequestRepository {
    private final DeviceCommandRequestMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String requestedTopic;

    public MybatisDeviceCommandRequestRepository(DeviceCommandRequestMapper mapper, ObjectMapper objectMapper,
            Clock clock, @Value("${octopus.kafka.command-requested-topic:octopus.local.command.requested.v1}")
            String requestedTopic) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.requestedTopic = requestedTopic;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceCommandRequest> findByIdempotency(UUID requestedBy, String idempotencyKey) {
        return Optional.ofNullable(mapper.findByIdempotency(requestedBy, idempotencyKey)).map(this::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<CommandDetails> find(UUID commandId) {
        return Optional.ofNullable(mapper.find(commandId))
                .map(row -> new CommandDetails(toDomain(row), row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(DeviceCommandRequest request, DeviceCommandRequested event) {
        if (!request.commandId().equals(event.commandId()) || !request.tenantId().equals(event.tenantId())) {
            throw new IllegalArgumentException("Command request event does not match projection");
        }
        if (mapper.insertCommand(toEntity(request)) != 1) throw new IllegalStateException("Unable to insert command request");
        if (mapper.insertOutbox(event.eventId(), event.tenantId(), requestedTopic, event.orderingKey(),
                DeviceCommandRequested.class.getSimpleName(), serialize(event), event.requestedAt()) != 1) {
            throw new IllegalStateException("Unable to append command request event");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void applyStatus(DeviceCommandStatusChanged event) {
        if (mapper.insertStatusInbox(event.tenantId(), event.eventId(), event.commandId(), clock.instant()) != 1) return;
        DeviceCommandRequestMapper.CommandRow row = Optional.ofNullable(mapper.findForUpdate(event.commandId()))
                .orElseThrow(() -> new IllegalStateException("Command status arrived before its request projection"));
        DeviceCommandRequest request = toDomain(row);
        if (request.apply(event) && mapper.updateCommand(toEntity(request), request.version() - 1) != 1) {
            throw new IllegalStateException("Unable to update command request projection");
        }
    }

    private DeviceCommandRequest toDomain(DeviceCommandRequestMapper.CommandRow row) {
        return DeviceCommandRequest.restore(row.commandId(), row.tenantId(), row.organizationId(), row.deviceId(),
                row.requestedBy(), row.idempotencyKey(), row.operation(), row.payloadSha256(),
                DeviceCommandStatusChanged.Status.valueOf(row.status()), row.failureCode(), row.requestedAt(),
                row.expiresAt(), row.dispatchedAt(), row.acknowledgedAt(), row.completedAt(),
                row.statusUpdatedAt(), row.version());
    }

    private static DeviceCommandRequestEntity toEntity(DeviceCommandRequest request) {
        DeviceCommandRequestEntity entity = new DeviceCommandRequestEntity();
        entity.setCommandId(request.commandId()); entity.setTenantId(request.tenantId());
        entity.setOrganizationId(request.organizationId()); entity.setDeviceId(request.deviceId());
        entity.setRequestedBy(request.requestedBy()); entity.setIdempotencyKey(request.idempotencyKey());
        entity.setOperation(request.operation()); entity.setPayloadSha256(request.payloadSha256());
        entity.setStatus(request.status().name()); entity.setFailureCode(request.failureCode().orElse(null));
        entity.setRequestedAt(request.requestedAt()); entity.setExpiresAt(request.expiresAt());
        entity.setDispatchedAt(request.dispatchedAt().orElse(null));
        entity.setAcknowledgedAt(request.acknowledgedAt().orElse(null));
        entity.setCompletedAt(request.completedAt().orElse(null));
        entity.setStatusUpdatedAt(request.statusUpdatedAt()); entity.setVersion(request.version());
        return entity;
    }

    private String serialize(DeviceCommandRequested event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize command request", failure);
        }
    }
}
