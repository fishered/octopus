package com.accuenergy.octopus.mgmt.application.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.dashboard.Dashboard;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID organizationId = UUID.randomUUID();
    private final UUID meterId = UUID.randomUUID();
    private final String organizationPath = "/root/site";
    private final Meter meter = Meter.create(meterId, tenant.value(), UUID.randomUUID(), null,
            UUID.randomUUID(), UUID.randomUUID(), "energy", "Energy", Meter.Kind.STANDARD,
            null, null, 3, ValueSemantics.CUMULATIVE, NOW);

    @Test
    void createsWithOrganizationZoneAndOwnerThenAddsAuthorizedWidget() throws Exception {
        MemoryDashboards repository = new MemoryDashboards(tenant.value(), organizationId, organizationPath);
        DashboardManagementService service = service(repository, new StubMeters(meter, organizationPath + "/floor"));
        AuthenticatedPrincipal principal = operator("dashboard:configure", "dashboard:view", "analytics:view");

        Dashboard created = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.create(principal,
                new DashboardManagementService.CreateDashboard(organizationId, "energy", "Energy", null, null)));
        Dashboard updated = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.addWidget(principal,
                created.id(), 0, new DashboardManagementService.AddWidget(meterId, "Consumption",
                        Dashboard.Visualization.BAR, TelemetryQuery.ValueField.INTERVAL_ACCUMULATION,
                        TelemetryQuery.Aggregation.SUM, 900, new Dashboard.Layout(0, 0, 12, 4), 0)));

        assertEquals(principal.accountId(), created.ownerAccountId());
        assertEquals("Europe/London", created.defaultZoneId());
        assertEquals(1, updated.version());
        assertEquals(meterId, updated.widgets().getFirst().meterId());
    }

    @Test
    void widgetRequiresAnalyticsPermissionAndMeterInDashboardSubtree() {
        MemoryDashboards repository = existingDashboard();
        DashboardManagementService missingPermission = service(repository,
                new StubMeters(meter, organizationPath + "/floor"));
        var command = widgetCommand();

        assertThrows(DashboardAccessDeniedException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> missingPermission.addWidget(operator("dashboard:configure"),
                        repository.dashboard.id(), 0, command)));

        MemoryDashboards outsideRepository = existingDashboard();
        DashboardManagementService outsideTree = service(outsideRepository, new StubMeters(meter, "/root/other"));
        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> outsideTree.addWidget(operator("dashboard:configure", "analytics:view"),
                        outsideRepository.dashboard.id(), 0, command)));
    }

    @Test
    void rejectsStaleIfMatchBeforePersistingWidget() {
        MemoryDashboards repository = existingDashboard();
        DashboardManagementService service = service(repository, new StubMeters(meter, organizationPath));

        assertThrows(DashboardVersionConflictException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.addWidget(operator("dashboard:configure", "analytics:view"),
                                repository.dashboard.id(), 4, widgetCommand())));
        assertEquals(0, repository.dashboard.widgets().size());
    }

    @Test
    void listsUpdatesRemovesAndArchivesWithOptimisticVersions() throws Exception {
        MemoryDashboards repository = existingDashboard();
        Dashboard.Widget widget = Dashboard.Widget.create(UUID.randomUUID(), meterId, "Consumption",
                Dashboard.Visualization.LINE, TelemetryQuery.ValueField.INTERVAL_ACCUMULATION,
                TelemetryQuery.Aggregation.SUM, 300, new Dashboard.Layout(0, 0, 12, 4), 0, NOW);
        repository.dashboard.addWidget(widget, NOW);
        DashboardManagementService service = service(repository, new StubMeters(meter, organizationPath));
        AuthenticatedPrincipal principal = operator("dashboard:view", "dashboard:configure", "analytics:view");

        List<Dashboard> listed = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.list(principal, organizationId, false, 100));
        Dashboard metadata = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.updateMetadata(
                principal, repository.dashboard.id(), 1,
                new DashboardManagementService.UpdateDashboard("Updated", "Description", "Asia/Shanghai")));
        Dashboard widgetUpdated = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.updateWidget(
                principal, repository.dashboard.id(), widget.id(), 2,
                new DashboardManagementService.UpdateWidget(meterId, "Peak", Dashboard.Visualization.GAUGE,
                        TelemetryQuery.ValueField.RAW_VALUE, TelemetryQuery.Aggregation.MAX, 60,
                        new Dashboard.Layout(0, 0, 6, 4), 10)));
        String updatedTitle = widgetUpdated.widgets().getFirst().title();
        Dashboard removed = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.removeWidget(
                principal, repository.dashboard.id(), widget.id(), 3));
        Dashboard archived = TenantContext.call(new TenantScope.Scoped(tenant), () -> service.archive(
                principal, repository.dashboard.id(), 4));

        assertEquals(1, listed.size());
        assertEquals("Asia/Shanghai", metadata.defaultZoneId());
        assertEquals("Peak", updatedTitle);
        assertEquals(0, removed.widgets().size());
        assertEquals(Dashboard.Status.ARCHIVED, archived.status());
        assertEquals(5, archived.version());
    }

    private DashboardManagementService service(MemoryDashboards dashboards, MeterRepository meters) {
        return new DashboardManagementService(dashboards, meters, new AuthorizationPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MemoryDashboards existingDashboard() {
        MemoryDashboards repository = new MemoryDashboards(tenant.value(), organizationId, organizationPath);
        repository.dashboard = Dashboard.create(UUID.randomUUID(), tenant.value(), organizationId,
                UUID.randomUUID(), "existing", "Existing", null, "UTC", NOW);
        return repository;
    }

    private DashboardManagementService.AddWidget widgetCommand() {
        return new DashboardManagementService.AddWidget(meterId, "Consumption", Dashboard.Visualization.LINE,
                TelemetryQuery.ValueField.INTERVAL_ACCUMULATION, TelemetryQuery.Aggregation.SUM,
                300, new Dashboard.Layout(0, 0, 12, 4), 0);
    }

    private AuthenticatedPrincipal operator(String... permissions) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permissions),
                Set.of("/root"), Set.of());
    }

    private static final class MemoryDashboards implements DashboardRepository {
        private final UUID tenantId;
        private final UUID organizationId;
        private final String path;
        private Dashboard dashboard;

        private MemoryDashboards(UUID tenantId, UUID organizationId, String path) {
            this.tenantId = tenantId;
            this.organizationId = organizationId;
            this.path = path;
        }

        public boolean existsByCode(String code) { return dashboard != null && dashboard.code().equals(code); }
        public Optional<OrganizationContext> findOrganizationContext(UUID requestedId) {
            return organizationId.equals(requestedId)
                    ? Optional.of(new OrganizationContext(tenantId, path, "Europe/London")) : Optional.empty();
        }
        public void insert(Dashboard value) { dashboard = value; }
        public Optional<DashboardDetails> find(UUID dashboardId) {
            return dashboard != null && dashboard.id().equals(dashboardId)
                    ? Optional.of(new DashboardDetails(dashboard, path)) : Optional.empty();
        }
        public List<DashboardDetails> findByOrganizationPath(String requestedPath,
                boolean includeArchived, int limit) {
            if (dashboard == null || !path.startsWith(requestedPath)
                    || (!includeArchived && dashboard.status() == Dashboard.Status.ARCHIVED)) return List.of();
            return List.of(new DashboardDetails(dashboard, path));
        }
        public boolean update(Dashboard value, long expectedVersion) {
            dashboard = value;
            return true;
        }
        public boolean addWidget(Dashboard value, Dashboard.Widget widget, long expectedVersion) {
            dashboard = value;
            return true;
        }
        public boolean updateWidget(Dashboard value, Dashboard.Widget widget, long expectedVersion) {
            dashboard = value;
            return true;
        }
        public boolean removeWidget(Dashboard value, UUID widgetId, long expectedVersion) {
            dashboard = value;
            return true;
        }
    }

    private static final class StubMeters implements MeterRepository {
        private final Meter meter;
        private final String path;
        private StubMeters(Meter meter, String path) { this.meter = meter; this.path = path; }
        public boolean existsByCode(String code) { return false; }
        public Optional<DeviceContext> findDeviceContext(UUID deviceId) { return Optional.empty(); }
        public Optional<ParameterConfiguration> findParameterConfiguration(UUID parameterId, UUID unitId) {
            return Optional.empty();
        }
        public boolean isBoundToDeviceModel(UUID deviceId, UUID parameterId, UUID unitId) { return false; }
        public boolean facilityExists(UUID facilityId) { return false; }
        public void insert(Meter value, MeterConfigurationChanged event) { }
        public Optional<MeterDetails> findDetails(UUID requestedId) {
            return meter.id().equals(requestedId)
                    ? Optional.of(new MeterDetails(meter, path, "kWh", "UTC")) : Optional.empty();
        }
    }
}
