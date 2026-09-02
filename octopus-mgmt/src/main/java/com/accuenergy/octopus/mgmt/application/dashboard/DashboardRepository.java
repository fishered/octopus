package com.accuenergy.octopus.mgmt.application.dashboard;

import com.accuenergy.octopus.mgmt.domain.dashboard.Dashboard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardRepository {
    boolean existsByCode(String code);
    Optional<OrganizationContext> findOrganizationContext(UUID organizationId);
    void insert(Dashboard dashboard);
    Optional<DashboardDetails> find(UUID dashboardId);
    List<DashboardDetails> findByOrganizationPath(String organizationPath, boolean includeArchived, int limit);
    boolean update(Dashboard dashboard, long expectedVersion);
    boolean addWidget(Dashboard dashboard, Dashboard.Widget widget, long expectedVersion);
    boolean updateWidget(Dashboard dashboard, Dashboard.Widget widget, long expectedVersion);
    boolean removeWidget(Dashboard dashboard, UUID widgetId, long expectedVersion);

    record OrganizationContext(UUID tenantId, String organizationPath, String effectiveZoneId) { }
    record DashboardDetails(Dashboard dashboard, String organizationPath) { }
}
