package com.accuenergy.octopus.mgmt.infrastructure.persistence.alarm;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.api.telemetry.ReadingQuality;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmRepository;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmIncident;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmRule;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisAlarmRepository implements AlarmRepository {
    private final AlarmMapper mapper;
    private final Clock clock;

    public MybatisAlarmRepository(AlarmMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean ruleCodeExists(String code) {
        return mapper.countRuleCode(code) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<MeterContext> findMeterContext(UUID meterId) {
        return Optional.ofNullable(mapper.findMeterContext(meterId)).map(row -> new MeterContext(
                row.tenantId(), row.organizationId(), row.deviceId(), row.parameterId(), row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceContext> findDeviceContext(UUID deviceId) {
        return Optional.ofNullable(mapper.findDeviceContext(deviceId))
                .map(row -> new DeviceContext(row.tenantId(), row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insertRule(AlarmRule rule) {
        if (mapper.insertRule(toEntity(rule)) != 1) throw new IllegalStateException("Unable to insert alarm rule");
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<RuleDetails> findRule(UUID ruleId) {
        return Optional.ofNullable(mapper.findRule(ruleId))
                .map(row -> new RuleDetails(toRule(row), row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<IncidentDetails> findIncident(UUID incidentId) {
        return Optional.ofNullable(mapper.findIncident(incidentId))
                .map(row -> new IncidentDetails(toIncident(row), row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<AlarmIncident> findDeviceIncidents(UUID deviceId, Set<AlarmIncident.State> states, int limit) {
        Set<String> names = states.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
        return mapper.findDeviceIncidents(deviceId, names, limit).stream().map(MybatisAlarmRepository::toIncident).toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void updateIncident(AlarmIncident incident) {
        long expectedVersion = incident.version() == 0 ? 0 : incident.version() - 1;
        if (mapper.updateIncident(toEntity(incident), expectedVersion) != 1) {
            throw new IllegalStateException("Alarm incident was concurrently modified");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void evaluate(NormalizedTelemetry telemetry) {
        var processedAt = clock.instant();
        List<AlarmMapper.AlarmRuleRow> rows = mapper.findActiveRules(telemetry.meterId(), telemetry.parameterId());
        for (AlarmMapper.AlarmRuleRow row : rows) {
            AlarmRule rule = toRule(row);
            if (!rule.matchesScope(telemetry.tenantId(), telemetry.deviceId(), telemetry.meterId(),
                    telemetry.parameterId())) {
                throw new IllegalStateException("Persisted alarm rule scope does not match telemetry");
            }
            if (mapper.insertEvaluation(telemetry.tenantId(), rule.id(), telemetry.sourceEventId(),
                    telemetry.occurredAt(), processedAt) != 1) {
                continue;
            }
            if (telemetry.quality().contains(ReadingQuality.INVALID)
                    || telemetry.quality().contains(ReadingQuality.OUT_OF_ORDER)) {
                continue;
            }
            Optional<AlarmIncident> current = Optional.ofNullable(
                    mapper.findOpenIncidentForUpdate(rule.id(), telemetry.meterId()))
                    .map(MybatisAlarmRepository::toIncident);
            var value = rule.selectedValue(telemetry.rawValue(), telemetry.delta(), telemetry.intervalAccumulation());
            if (rule.isTriggered(value)) {
                if (current.isEmpty()) {
                    AlarmIncident opened = AlarmIncident.open(UUID.randomUUID(), rule, value,
                            telemetry.occurredAt(), processedAt);
                    if (mapper.insertIncident(toEntity(opened)) != 1) {
                        throw new IllegalStateException("Unable to open alarm incident");
                    }
                } else if (current.orElseThrow().observe(value, telemetry.occurredAt(), processedAt)) {
                    updateLocked(current.orElseThrow());
                }
            } else if (current.isPresent() && rule.shouldClear(value)
                    && current.orElseThrow().clear(value, telemetry.occurredAt(), processedAt)) {
                updateLocked(current.orElseThrow());
            }
        }
    }

    private void updateLocked(AlarmIncident incident) {
        if (mapper.updateIncident(toEntity(incident), incident.version() - 1) != 1) {
            throw new IllegalStateException("Unable to update locked alarm incident");
        }
    }

    private static AlarmRule toRule(AlarmMapper.AlarmRuleRow row) {
        return AlarmRule.restore(row.id(), row.tenantId(), row.organizationId(), row.deviceId(), row.meterId(),
                row.parameterId(), row.code(), row.displayName(), AlarmRule.ValueSelector.valueOf(row.valueSelector()),
                AlarmRule.Comparison.valueOf(row.comparison()), row.triggerThreshold(), row.clearThreshold(),
                AlarmRule.Severity.valueOf(row.severity()), AlarmRule.Status.valueOf(row.status()), row.version(),
                row.createdAt(), row.updatedAt());
    }

    private static AlarmIncident toIncident(AlarmMapper.AlarmIncidentRow row) {
        return AlarmIncident.restore(row.id(), row.tenantId(), row.ruleId(), row.organizationId(), row.deviceId(),
                row.meterId(), row.parameterId(), AlarmRule.Severity.valueOf(row.severity()),
                AlarmIncident.State.valueOf(row.state()), row.triggerValue(), row.latestValue(), row.occurrenceCount(),
                row.openedAt(), row.lastObservedAt(), row.acknowledgedBy(), row.acknowledgedAt(), row.clearedAt(),
                row.version(), row.updatedAt());
    }

    private static AlarmRuleEntity toEntity(AlarmRule rule) {
        AlarmRuleEntity entity = new AlarmRuleEntity();
        entity.setId(rule.id()); entity.setTenantId(rule.tenantId()); entity.setOrganizationId(rule.organizationId());
        entity.setDeviceId(rule.deviceId()); entity.setMeterId(rule.meterId()); entity.setParameterId(rule.parameterId());
        entity.setCode(rule.code()); entity.setDisplayName(rule.displayName());
        entity.setValueSelector(rule.valueSelector().name()); entity.setComparison(rule.comparison().name());
        entity.setTriggerThreshold(rule.triggerThreshold()); entity.setClearThreshold(rule.clearThreshold());
        entity.setSeverity(rule.severity().name()); entity.setStatus(rule.status().name());
        entity.setVersion(rule.version()); entity.setCreatedAt(rule.createdAt()); entity.setUpdatedAt(rule.updatedAt());
        return entity;
    }

    private static AlarmIncidentEntity toEntity(AlarmIncident incident) {
        AlarmIncidentEntity entity = new AlarmIncidentEntity();
        entity.setId(incident.id()); entity.setTenantId(incident.tenantId()); entity.setRuleId(incident.ruleId());
        entity.setOrganizationId(incident.organizationId()); entity.setDeviceId(incident.deviceId());
        entity.setMeterId(incident.meterId()); entity.setParameterId(incident.parameterId());
        entity.setSeverity(incident.severity().name()); entity.setState(incident.state().name());
        entity.setTriggerValue(incident.triggerValue()); entity.setLatestValue(incident.latestValue());
        entity.setOccurrenceCount(incident.occurrenceCount()); entity.setOpenedAt(incident.openedAt());
        entity.setLastObservedAt(incident.lastObservedAt());
        entity.setAcknowledgedBy(incident.acknowledgedBy().orElse(null));
        entity.setAcknowledgedAt(incident.acknowledgedAt().orElse(null));
        entity.setClearedAt(incident.clearedAt().orElse(null));
        entity.setVersion(incident.version()); entity.setUpdatedAt(incident.updatedAt());
        return entity;
    }
}
