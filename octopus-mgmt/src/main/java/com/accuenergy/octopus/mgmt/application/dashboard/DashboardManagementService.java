package com.accuenergy.octopus.mgmt.application.dashboard;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuerySemantics;
import com.accuenergy.octopus.mgmt.domain.dashboard.Dashboard;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DashboardManagementService {
    private final DashboardRepository dashboards;
    private final MeterRepository meters;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public DashboardManagementService(DashboardRepository dashboards, MeterRepository meters,
            AuthorizationPolicy authorization, Clock clock) {
        this.dashboards = dashboards;
        this.meters = meters;
        this.authorization = authorization;
        this.clock = clock;
    }

    public Dashboard create(AuthenticatedPrincipal principal, CreateDashboard command) {
        TenantId tenantId = currentTenant();
        DashboardRepository.OrganizationContext organization = dashboards
                .findOrganizationContext(command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown active organization"));
        if (!tenantId.value().equals(organization.tenantId())) throw new DashboardAccessDeniedException();
        requireAllowed(principal, "dashboard:configure", ResourceAction.CONFIGURE, tenantId,
                "organization", command.organizationId(), organization.organizationPath());
        if (dashboards.existsByCode(command.code())) {
            throw new IllegalArgumentException("Dashboard code already exists");
        }
        String zoneId = command.defaultZoneId() == null || command.defaultZoneId().isBlank()
                ? organization.effectiveZoneId() : command.defaultZoneId().strip();
        Dashboard dashboard = Dashboard.create(UUID.randomUUID(), tenantId.value(), command.organizationId(),
                principal.accountId(), command.code(), command.displayName(), command.description(),
                zoneId, clock.instant());
        dashboards.insert(dashboard);
        return dashboard;
    }

    public Dashboard get(AuthenticatedPrincipal principal, UUID dashboardId) {
        DashboardRepository.DashboardDetails details = dashboards.find(dashboardId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard"));
        TenantId tenantId = currentTenant();
        if (!tenantId.value().equals(details.dashboard().tenantId())) throw new DashboardAccessDeniedException();
        requireAllowed(principal, "dashboard:view", ResourceAction.VIEW, tenantId,
                "dashboard", dashboardId, details.organizationPath());
        return details.dashboard();
    }

    public List<Dashboard> list(AuthenticatedPrincipal principal, UUID organizationId,
            boolean includeArchived, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        TenantId tenantId = currentTenant();
        DashboardRepository.OrganizationContext organization = dashboards.findOrganizationContext(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown active organization"));
        if (!tenantId.value().equals(organization.tenantId())) throw new DashboardAccessDeniedException();
        requireAllowed(principal, "dashboard:view", ResourceAction.VIEW, tenantId,
                "organization", organizationId, organization.organizationPath());
        return dashboards.findByOrganizationPath(organization.organizationPath(), includeArchived, limit).stream()
                .map(DashboardRepository.DashboardDetails::dashboard)
                .toList();
    }

    public Dashboard updateMetadata(AuthenticatedPrincipal principal, UUID dashboardId,
            long expectedVersion, UpdateDashboard command) {
        DashboardRepository.DashboardDetails details = findForConfiguration(principal, dashboardId);
        Dashboard dashboard = details.dashboard();
        requireVersion(dashboard, expectedVersion);
        dashboard.updateMetadata(command.displayName(), command.description(), command.defaultZoneId(),
                clock.instant());
        if (!dashboards.update(dashboard, expectedVersion)) throw new DashboardVersionConflictException();
        return dashboard;
    }

    public Dashboard archive(AuthenticatedPrincipal principal, UUID dashboardId, long expectedVersion) {
        DashboardRepository.DashboardDetails details = findForConfiguration(principal, dashboardId);
        Dashboard dashboard = details.dashboard();
        requireVersion(dashboard, expectedVersion);
        if (dashboard.archive(clock.instant()) && !dashboards.update(dashboard, expectedVersion)) {
            throw new DashboardVersionConflictException();
        }
        return dashboard;
    }

    public Dashboard addWidget(AuthenticatedPrincipal principal, UUID dashboardId,
            long expectedVersion, AddWidget command) {
        DashboardRepository.DashboardDetails details = findForConfiguration(principal, dashboardId);
        Dashboard dashboard = details.dashboard();
        requireVersion(dashboard, expectedVersion);
        validateWidgetMeter(principal, command.meterId(), command.field(), details.organizationPath());

        var now = clock.instant();
        Dashboard.Widget widget = Dashboard.Widget.create(UUID.randomUUID(), command.meterId(), command.title(),
                command.visualization(), command.field(), command.aggregation(), command.bucketSeconds(),
                command.layout(), command.sortOrder(), now);
        dashboard.addWidget(widget, now);
        if (!dashboards.addWidget(dashboard, widget, expectedVersion)) {
            throw new DashboardVersionConflictException();
        }
        return dashboard;
    }

    public Dashboard updateWidget(AuthenticatedPrincipal principal, UUID dashboardId, UUID widgetId,
            long expectedVersion, UpdateWidget command) {
        DashboardRepository.DashboardDetails details = findForConfiguration(principal, dashboardId);
        Dashboard dashboard = details.dashboard();
        requireVersion(dashboard, expectedVersion);
        validateWidgetMeter(principal, command.meterId(), command.field(), details.organizationPath());
        Dashboard.Widget widget = dashboard.updateWidget(widgetId, command.meterId(), command.title(),
                command.visualization(), command.field(), command.aggregation(), command.bucketSeconds(),
                command.layout(), command.sortOrder(), clock.instant());
        if (!dashboards.updateWidget(dashboard, widget, expectedVersion)) {
            throw new DashboardVersionConflictException();
        }
        return dashboard;
    }

    public Dashboard removeWidget(AuthenticatedPrincipal principal, UUID dashboardId, UUID widgetId,
            long expectedVersion) {
        DashboardRepository.DashboardDetails details = findForConfiguration(principal, dashboardId);
        Dashboard dashboard = details.dashboard();
        requireVersion(dashboard, expectedVersion);
        dashboard.removeWidget(widgetId, clock.instant());
        if (!dashboards.removeWidget(dashboard, widgetId, expectedVersion)) {
            throw new DashboardVersionConflictException();
        }
        return dashboard;
    }

    private DashboardRepository.DashboardDetails findForConfiguration(AuthenticatedPrincipal principal,
            UUID dashboardId) {
        DashboardRepository.DashboardDetails details = dashboards.find(dashboardId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard"));
        TenantId tenantId = currentTenant();
        if (!tenantId.value().equals(details.dashboard().tenantId())) throw new DashboardAccessDeniedException();
        requireAllowed(principal, "dashboard:configure", ResourceAction.CONFIGURE, tenantId,
                "dashboard", dashboardId, details.organizationPath());
        return details;
    }

    private void validateWidgetMeter(AuthenticatedPrincipal principal, UUID meterId,
            TelemetryQuery.ValueField field, String dashboardOrganizationPath) {
        TenantId tenantId = currentTenant();
        MeterRepository.MeterDetails meterDetails = meters.findDetails(meterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown meter"));
        Meter meter = meterDetails.meter();
        if (!tenantId.value().equals(meter.tenantId()) || meter.status() != Meter.Status.ACTIVE) {
            throw new DashboardAccessDeniedException();
        }
        requireAllowed(principal, "analytics:view", ResourceAction.VIEW, tenantId,
                "meter", meter.id(), meterDetails.organizationPath());
        if (!isPathWithin(meterDetails.organizationPath(), dashboardOrganizationPath)) {
            throw new IllegalArgumentException("Widget meter must belong to the dashboard organization subtree");
        }
        TelemetryQuerySemantics.validate(meter.semantics(), field);
    }

    private static void requireVersion(Dashboard dashboard, long expectedVersion) {
        if (dashboard.version() != expectedVersion) throw new DashboardVersionConflictException();
    }

    private void requireAllowed(AuthenticatedPrincipal principal, String permission, ResourceAction action,
            TenantId tenantId, String resourceType, UUID resourceId, String organizationPath) {
        if (!authorization.isAllowed(principal, permission, action,
                new ProtectedResource(tenantId, resourceType, resourceId, Optional.of(organizationPath)))) {
            throw new DashboardAccessDeniedException();
        }
    }

    private static boolean isPathWithin(String candidate, String scope) {
        if (candidate == null || scope == null || candidate.isBlank() || scope.isBlank()) return false;
        String normalizedCandidate = candidate.endsWith("/") ? candidate : candidate + "/";
        String normalizedScope = scope.endsWith("/") ? scope : scope + "/";
        return normalizedCandidate.startsWith(normalizedScope);
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Dashboard operations require tenant scope"));
    }

    public record CreateDashboard(UUID organizationId, String code, String displayName,
                                  String description, String defaultZoneId) { }

    public record AddWidget(UUID meterId, String title, Dashboard.Visualization visualization,
                            TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
                            long bucketSeconds, Dashboard.Layout layout, int sortOrder) { }

    public record UpdateDashboard(String displayName, String description, String defaultZoneId) { }

    public record UpdateWidget(UUID meterId, String title, Dashboard.Visualization visualization,
                               TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
                               long bucketSeconds, Dashboard.Layout layout, int sortOrder) { }
}
