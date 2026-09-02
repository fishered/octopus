package com.accuenergy.octopus.mgmt.infrastructure.persistence.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {
    long countCode(@Param("code") String code);
    OrganizationContextRow findOrganizationContext(@Param("organizationId") UUID organizationId);
    int insertDashboard(@Param("dashboard") DashboardEntity dashboard);
    DashboardRow findDashboard(@Param("dashboardId") UUID dashboardId);
    List<DashboardRow> findDashboardsByOrganizationPath(@Param("organizationPath") String organizationPath,
            @Param("includeArchived") boolean includeArchived, @Param("limit") int limit);
    List<WidgetRow> findWidgets(@Param("dashboardId") UUID dashboardId);
    int updateDashboard(@Param("dashboard") DashboardEntity dashboard,
            @Param("expectedVersion") long expectedVersion);
    int updateDashboardVersion(@Param("dashboardId") UUID dashboardId,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt);
    int insertWidget(@Param("widget") DashboardWidgetEntity widget);
    int updateWidget(@Param("widget") DashboardWidgetEntity widget);
    int deleteWidget(@Param("dashboardId") UUID dashboardId, @Param("widgetId") UUID widgetId);

    record OrganizationContextRow(UUID tenantId, String organizationPath, String effectiveZoneId) { }
    record DashboardRow(UUID id, UUID tenantId, UUID organizationId, UUID ownerAccountId,
            String code, String displayName, String description, String defaultZoneId,
            String status, long version, Instant createdAt, Instant updatedAt,
            String organizationPath) { }
    record WidgetRow(UUID id, UUID meterId, String title, String visualization, String valueField,
            String aggregation, long bucketSeconds, String layoutJson, int sortOrder,
            Instant createdAt, Instant updatedAt) { }
}
