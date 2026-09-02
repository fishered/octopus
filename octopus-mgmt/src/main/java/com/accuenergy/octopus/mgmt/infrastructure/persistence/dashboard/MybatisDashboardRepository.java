package com.accuenergy.octopus.mgmt.infrastructure.persistence.dashboard;

import com.accuenergy.octopus.mgmt.application.dashboard.DashboardRepository;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.accuenergy.octopus.mgmt.domain.dashboard.Dashboard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDashboardRepository implements DashboardRepository {
    private final DashboardMapper mapper;
    private final ObjectMapper objectMapper;

    public MybatisDashboardRepository(DashboardMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean existsByCode(String code) {
        return mapper.countCode(code) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<OrganizationContext> findOrganizationContext(UUID organizationId) {
        return Optional.ofNullable(mapper.findOrganizationContext(organizationId))
                .map(row -> new OrganizationContext(row.tenantId(), row.organizationPath(), row.effectiveZoneId()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(Dashboard dashboard) {
        if (mapper.insertDashboard(toEntity(dashboard)) != 1) {
            throw new IllegalStateException("Unable to insert dashboard");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DashboardDetails> find(UUID dashboardId) {
        DashboardMapper.DashboardRow row = mapper.findDashboard(dashboardId);
        if (row == null) return Optional.empty();
        var widgets = mapper.findWidgets(dashboardId).stream().map(this::restoreWidget).toList();
        Dashboard dashboard = Dashboard.restore(row.id(), row.tenantId(), row.organizationId(),
                row.ownerAccountId(), row.code(), row.displayName(), row.description(), row.defaultZoneId(),
                Dashboard.Status.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt(), widgets);
        return Optional.of(new DashboardDetails(dashboard, row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<DashboardDetails> findByOrganizationPath(String organizationPath,
            boolean includeArchived, int limit) {
        return mapper.findDashboardsByOrganizationPath(organizationPath, includeArchived, limit).stream()
                .map(row -> new DashboardDetails(restoreDashboard(row, List.of()), row.organizationPath()))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public boolean update(Dashboard dashboard, long expectedVersion) {
        return mapper.updateDashboard(toEntity(dashboard), expectedVersion) == 1;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public boolean addWidget(Dashboard dashboard, Dashboard.Widget widget, long expectedVersion) {
        int updated = mapper.updateDashboardVersion(dashboard.id(), expectedVersion,
                dashboard.version(), dashboard.updatedAt());
        if (updated == 0) return false;
        if (mapper.insertWidget(toEntity(dashboard, widget)) != 1) {
            throw new IllegalStateException("Unable to insert dashboard widget");
        }
        return true;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public boolean updateWidget(Dashboard dashboard, Dashboard.Widget widget, long expectedVersion) {
        int updated = mapper.updateDashboardVersion(dashboard.id(), expectedVersion,
                dashboard.version(), dashboard.updatedAt());
        if (updated == 0) return false;
        if (mapper.updateWidget(toEntity(dashboard, widget)) != 1) {
            throw new IllegalStateException("Unable to update dashboard widget");
        }
        return true;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public boolean removeWidget(Dashboard dashboard, UUID widgetId, long expectedVersion) {
        int updated = mapper.updateDashboardVersion(dashboard.id(), expectedVersion,
                dashboard.version(), dashboard.updatedAt());
        if (updated == 0) return false;
        if (mapper.deleteWidget(dashboard.id(), widgetId) != 1) {
            throw new IllegalStateException("Unable to delete dashboard widget");
        }
        return true;
    }

    private static Dashboard restoreDashboard(DashboardMapper.DashboardRow row, List<Dashboard.Widget> widgets) {
        return Dashboard.restore(row.id(), row.tenantId(), row.organizationId(),
                row.ownerAccountId(), row.code(), row.displayName(), row.description(), row.defaultZoneId(),
                Dashboard.Status.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt(), widgets);
    }

    private Dashboard.Widget restoreWidget(DashboardMapper.WidgetRow row) {
        try {
            Dashboard.Layout layout = objectMapper.readValue(row.layoutJson(), Dashboard.Layout.class);
            return Dashboard.Widget.restore(row.id(), row.meterId(), row.title(),
                    Dashboard.Visualization.valueOf(row.visualization()),
                    TelemetryQuery.ValueField.valueOf(row.valueField()),
                    TelemetryQuery.Aggregation.valueOf(row.aggregation()), row.bucketSeconds(), layout,
                    row.sortOrder(), row.createdAt(), row.updatedAt());
        } catch (JsonProcessingException invalidLayout) {
            throw new IllegalStateException("Stored dashboard widget layout is invalid", invalidLayout);
        }
    }

    private static DashboardEntity toEntity(Dashboard dashboard) {
        DashboardEntity entity = new DashboardEntity();
        entity.setId(dashboard.id());
        entity.setTenantId(dashboard.tenantId());
        entity.setOrganizationId(dashboard.organizationId());
        entity.setOwnerAccountId(dashboard.ownerAccountId());
        entity.setCode(dashboard.code());
        entity.setDisplayName(dashboard.displayName());
        entity.setDescription(dashboard.description().orElse(null));
        entity.setDefaultZoneId(dashboard.defaultZoneId());
        entity.setStatus(dashboard.status().name());
        entity.setVersion(dashboard.version());
        entity.setCreatedAt(dashboard.createdAt());
        entity.setUpdatedAt(dashboard.updatedAt());
        return entity;
    }

    private DashboardWidgetEntity toEntity(Dashboard dashboard, Dashboard.Widget widget) {
        DashboardWidgetEntity entity = new DashboardWidgetEntity();
        entity.setId(widget.id());
        entity.setTenantId(dashboard.tenantId());
        entity.setDashboardId(dashboard.id());
        entity.setMeterId(widget.meterId());
        entity.setTitle(widget.title());
        entity.setVisualization(widget.visualization().name());
        entity.setValueField(widget.field().name());
        entity.setAggregation(widget.aggregation().name());
        entity.setBucketSeconds(widget.bucketSeconds());
        try {
            entity.setLayoutJson(objectMapper.writeValueAsString(widget.layout()));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Unable to serialize dashboard widget layout", impossible);
        }
        entity.setSortOrder(widget.sortOrder());
        entity.setCreatedAt(widget.createdAt());
        entity.setUpdatedAt(widget.updatedAt());
        return entity;
    }
}
