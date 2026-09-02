package com.accuenergy.octopus.mgmt.infrastructure.persistence.alarm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlarmMapper {
    long countRuleCode(@Param("code") String code);
    MeterContextRow findMeterContext(@Param("meterId") UUID meterId);
    DeviceContextRow findDeviceContext(@Param("deviceId") UUID deviceId);
    int insertRule(AlarmRuleEntity entity);
    AlarmRuleRow findRule(@Param("ruleId") UUID ruleId);
    AlarmIncidentRow findIncident(@Param("incidentId") UUID incidentId);
    List<AlarmIncidentRow> findDeviceIncidents(@Param("deviceId") UUID deviceId,
            @Param("states") Set<String> states, @Param("limit") int limit);
    List<AlarmRuleRow> findActiveRules(@Param("meterId") UUID meterId, @Param("parameterId") UUID parameterId);
    @InterceptorIgnore(tenantLine = "true")
    int insertEvaluation(@Param("tenantId") UUID tenantId, @Param("ruleId") UUID ruleId,
            @Param("sourceEventId") UUID sourceEventId, @Param("occurredAt") Instant occurredAt,
            @Param("processedAt") Instant processedAt);
    AlarmIncidentRow findOpenIncidentForUpdate(@Param("ruleId") UUID ruleId, @Param("meterId") UUID meterId);
    int insertIncident(AlarmIncidentEntity entity);
    int updateIncident(@Param("incident") AlarmIncidentEntity entity, @Param("expectedVersion") long expectedVersion);

    record MeterContextRow(UUID tenantId, UUID organizationId, UUID deviceId, UUID parameterId,
                           String organizationPath) { }
    record DeviceContextRow(UUID tenantId, String organizationPath) { }
    record AlarmRuleRow(UUID id, UUID tenantId, UUID organizationId, UUID deviceId, UUID meterId,
                        UUID parameterId, String code, String displayName, String valueSelector,
                        String comparison, BigDecimal triggerThreshold, BigDecimal clearThreshold,
                        String severity, String status, long version, Instant createdAt, Instant updatedAt,
                        String organizationPath) { }
    record AlarmIncidentRow(UUID id, UUID tenantId, UUID ruleId, UUID organizationId, UUID deviceId,
                            UUID meterId, UUID parameterId, String severity, String state,
                            BigDecimal triggerValue, BigDecimal latestValue, long occurrenceCount,
                            Instant openedAt, Instant lastObservedAt, UUID acknowledgedBy,
                            Instant acknowledgedAt, Instant clearedAt, long version, Instant updatedAt,
                            String organizationPath) { }
}
