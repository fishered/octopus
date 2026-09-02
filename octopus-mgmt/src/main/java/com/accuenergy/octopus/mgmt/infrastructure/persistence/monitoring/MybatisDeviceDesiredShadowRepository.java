package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.mgmt.application.monitoring.DesiredShadowIdempotencyConflictException;
import com.accuenergy.octopus.mgmt.application.monitoring.DesiredShadowVersionConflictException;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredShadowRepository;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import com.accuenergy.octopus.mgmt.domain.monitoring.DesiredShadowRequest;
import com.accuenergy.octopus.mgmt.infrastructure.persistence.control.DeviceCommandRequestEntity;
import com.accuenergy.octopus.mgmt.infrastructure.persistence.control.DeviceCommandRequestMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceDesiredShadowRepository implements DeviceDesiredShadowRepository {
    private final DeviceDesiredShadowMapper desired;
    private final DeviceCommandRequestMapper commands;
    private final ObjectMapper objectMapper;
    private final String requestedTopic;

    public MybatisDeviceDesiredShadowRepository(DeviceDesiredShadowMapper desired,
            DeviceCommandRequestMapper commands, ObjectMapper objectMapper,
            @Value("${octopus.kafka.command-requested-topic:octopus.local.command.requested.v1}")
            String requestedTopic) {
        this.desired = desired;
        this.commands = commands;
        this.objectMapper = objectMapper;
        this.requestedTopic = requestedTopic;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DesiredShadowRequest> findByIdempotency(UUID requestedBy, String idempotencyKey) {
        return Optional.ofNullable(desired.findByIdempotency(requestedBy, idempotencyKey)).map(this::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DesiredShadowRequest> findCurrent(UUID deviceId) {
        return Optional.ofNullable(desired.findCurrent(deviceId)).map(this::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public DesiredShadowRequest submit(DesiredShadowRequest request, DeviceCommandRequest command,
            DeviceCommandRequested event) {
        requireMatches(request, command, event);
        DeviceDesiredShadowMapper.DesiredRow existing = desired.findByIdempotencyForUpdate(
                request.requestedBy(), request.idempotencyKey());
        if (existing != null) {
            DesiredShadowRequest restored = toDomain(existing);
            if (!restored.matches(request.deviceId(), request.expectedVersion(), request.requestSha256())) {
                throw new DesiredShadowIdempotencyConflictException();
            }
            return restored;
        }

        DeviceDesiredShadowMapper.CurrentRow current = desired.lockCurrent(request.deviceId());
        existing = desired.findByIdempotencyForUpdate(request.requestedBy(), request.idempotencyKey());
        if (existing != null) {
            DesiredShadowRequest restored = toDomain(existing);
            if (!restored.matches(request.deviceId(), request.expectedVersion(), request.requestSha256())) {
                throw new DesiredShadowIdempotencyConflictException();
            }
            return restored;
        }
        long currentVersion = current == null ? 0 : current.desiredVersion();
        if (currentVersion != request.expectedVersion()) throw new DesiredShadowVersionConflictException();
        if (current != null) desired.markSuperseded(current.requestId(), request.requestedAt());
        if (desired.insertRequest(toRow(request)) != 1) {
            DesiredShadowRequest restored = Optional.ofNullable(desired.findByIdempotencyForUpdate(
                    request.requestedBy(), request.idempotencyKey())).map(this::toDomain)
                    .orElseThrow(() -> new IllegalStateException("Desired shadow request conflict disappeared"));
            if (!restored.matches(request.deviceId(), request.expectedVersion(), request.requestSha256())) {
                throw new DesiredShadowIdempotencyConflictException();
            }
            return restored;
        }
        DeviceDesiredShadowMapper.CurrentRow next = new DeviceDesiredShadowMapper.CurrentRow(request.tenantId(),
                request.deviceId(), request.desiredVersion(), request.requestId(), request.desiredStateJson(),
                request.requestedAt());
        int currentChanges = current == null ? desired.insertCurrent(next)
                : desired.updateCurrent(next, request.expectedVersion());
        if (currentChanges != 1) throw new DesiredShadowVersionConflictException();
        if (commands.insertCommand(toCommandEntity(command)) != 1) {
            throw new IllegalStateException("Unable to insert desired shadow command");
        }
        if (commands.insertOutbox(event.eventId(), event.tenantId(), requestedTopic, event.orderingKey(),
                DeviceCommandRequested.class.getSimpleName(), serialize(event), event.requestedAt()) != 1) {
            throw new IllegalStateException("Unable to append desired shadow command event");
        }
        return request;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void applyCommandStatus(DeviceCommandStatusChanged event) {
        DeviceDesiredShadowMapper.DesiredRow row = desired.findByCommandForUpdate(event.commandId());
        if (row == null) return;
        DesiredShadowRequest request = toDomain(row);
        if (request.applyCommandStatus(event)
                && desired.updateRequest(toRow(request), request.projectionVersion() - 1) != 1) {
            throw new IllegalStateException("Desired shadow request was concurrently modified");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void reconcile(DeviceShadowReported event) {
        if (event.appliedDesiredVersion() == null) return;
        DeviceDesiredShadowMapper.DesiredRow row = desired.findCurrentRequestForUpdate(event.deviceId());
        if (row == null) return;
        DesiredShadowRequest request = toDomain(row);
        if (!reportedContainsDesired(event.stateJson(), request.desiredStateJson())) return;
        if (request.markApplied(event.appliedDesiredVersion(), event.receivedAt())
                && desired.updateRequest(toRow(request), request.projectionVersion() - 1) != 1) {
            throw new IllegalStateException("Desired shadow reconciliation was concurrent");
        }
    }

    boolean reportedContainsDesired(String reportedJson, String desiredJson) {
        try {
            var reported = objectMapper.readTree(reportedJson);
            var expected = objectMapper.readTree(desiredJson);
            if (reported == null || !reported.isObject() || expected == null || !expected.isObject()) return false;
            var fieldNames = expected.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (!expected.get(fieldName).equals(reported.get(fieldName))) return false;
            }
            return true;
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Unable to reconcile desired and reported shadow JSON", malformed);
        }
    }

    private static void requireMatches(DesiredShadowRequest desired, DeviceCommandRequest command,
            DeviceCommandRequested event) {
        if (!desired.commandId().equals(command.commandId()) || !desired.commandId().equals(event.commandId())
                || !desired.tenantId().equals(command.tenantId()) || !desired.tenantId().equals(event.tenantId())
                || !desired.deviceId().equals(command.deviceId()) || !desired.deviceId().equals(event.deviceId())) {
            throw new IllegalArgumentException("Desired shadow request and command do not match");
        }
    }

    private DesiredShadowRequest toDomain(DeviceDesiredShadowMapper.DesiredRow row) {
        return DesiredShadowRequest.restore(row.requestId(), row.tenantId(), row.organizationId(), row.deviceId(),
                row.requestedBy(), row.idempotencyKey(), row.expectedVersion(), row.desiredVersion(),
                row.desiredStateJson(), row.requestSha256(), row.commandId(),
                DesiredShadowRequest.Status.valueOf(row.status()), row.failureCode(), row.requestedAt(),
                row.expiresAt(), row.appliedAt(), row.statusUpdatedAt(), row.projectionVersion());
    }

    private static DeviceDesiredShadowMapper.DesiredRow toRow(DesiredShadowRequest request) {
        return new DeviceDesiredShadowMapper.DesiredRow(request.requestId(), request.tenantId(),
                request.organizationId(), request.deviceId(), request.requestedBy(), request.idempotencyKey(),
                request.expectedVersion(), request.desiredVersion(), request.desiredStateJson(),
                request.requestSha256(), request.commandId(), request.status().name(),
                request.failureCode().orElse(null), request.requestedAt(), request.expiresAt(),
                request.appliedAt().orElse(null), request.statusUpdatedAt(), request.projectionVersion());
    }

    private static DeviceCommandRequestEntity toCommandEntity(DeviceCommandRequest command) {
        DeviceCommandRequestEntity entity = new DeviceCommandRequestEntity();
        entity.setCommandId(command.commandId()); entity.setTenantId(command.tenantId());
        entity.setOrganizationId(command.organizationId()); entity.setDeviceId(command.deviceId());
        entity.setRequestedBy(command.requestedBy()); entity.setIdempotencyKey(command.idempotencyKey());
        entity.setOperation(command.operation()); entity.setPayloadSha256(command.payloadSha256());
        entity.setStatus(command.status().name()); entity.setFailureCode(command.failureCode().orElse(null));
        entity.setRequestedAt(command.requestedAt()); entity.setExpiresAt(command.expiresAt());
        entity.setDispatchedAt(command.dispatchedAt().orElse(null));
        entity.setAcknowledgedAt(command.acknowledgedAt().orElse(null));
        entity.setCompletedAt(command.completedAt().orElse(null));
        entity.setStatusUpdatedAt(command.statusUpdatedAt()); entity.setVersion(command.version());
        return entity;
    }

    private String serialize(DeviceCommandRequested event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize desired shadow command", failure);
        }
    }
}
