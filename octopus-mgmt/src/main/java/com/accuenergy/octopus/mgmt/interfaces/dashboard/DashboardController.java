package com.accuenergy.octopus.mgmt.interfaces.dashboard;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.dashboard.DashboardManagementService;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.accuenergy.octopus.mgmt.domain.dashboard.Dashboard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public final class DashboardController {
    private final DashboardManagementService dashboards;

    public DashboardController(DashboardManagementService dashboards) {
        this.dashboards = dashboards;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_dashboard:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> create(Authentication authentication,
            @Valid @RequestBody CreateDashboardRequest request) {
        Dashboard dashboard = dashboards.create(principal(authentication),
                new DashboardManagementService.CreateDashboard(request.organizationId(), request.code(),
                        request.displayName(), request.description(), request.defaultZoneId()));
        return response(dashboard);
    }

    @GetMapping("/{dashboardId}")
    @PreAuthorize("hasAuthority('PERM_dashboard:view') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> get(Authentication authentication,
            @PathVariable UUID dashboardId) {
        return response(dashboards.get(principal(authentication), dashboardId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_dashboard:view') or hasAuthority('PERM_platform:all')")
    public List<DashboardSummaryResponse> list(Authentication authentication,
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "100") int limit) {
        return dashboards.list(principal(authentication), organizationId, includeArchived, limit).stream()
                .map(DashboardController::summary)
                .toList();
    }

    @PutMapping("/{dashboardId}")
    @PreAuthorize("hasAuthority('PERM_dashboard:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> update(Authentication authentication,
            @PathVariable UUID dashboardId, @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateDashboardRequest request) {
        return response(dashboards.updateMetadata(principal(authentication), dashboardId, parseVersion(ifMatch),
                new DashboardManagementService.UpdateDashboard(request.displayName(), request.description(),
                        request.defaultZoneId())));
    }

    @PostMapping("/{dashboardId}/archive")
    @PreAuthorize("hasAuthority('PERM_dashboard:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> archive(Authentication authentication,
            @PathVariable UUID dashboardId, @RequestHeader("If-Match") String ifMatch) {
        return response(dashboards.archive(principal(authentication), dashboardId, parseVersion(ifMatch)));
    }

    @PostMapping("/{dashboardId}/widgets")
    @PreAuthorize("hasAuthority('PERM_dashboard:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> addWidget(Authentication authentication,
            @PathVariable UUID dashboardId, @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody AddWidgetRequest request) {
        Dashboard.Layout layout = new Dashboard.Layout(request.layout().x(), request.layout().y(),
                request.layout().width(), request.layout().height());
        Dashboard dashboard = dashboards.addWidget(principal(authentication), dashboardId, parseVersion(ifMatch),
                new DashboardManagementService.AddWidget(request.meterId(), request.title(),
                        request.visualization(), request.field(), request.aggregation(), request.bucketSeconds(),
                        layout, request.sortOrder()));
        return response(dashboard);
    }

    @PutMapping("/{dashboardId}/widgets/{widgetId}")
    @PreAuthorize("hasAuthority('PERM_dashboard:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> updateWidget(Authentication authentication,
            @PathVariable UUID dashboardId, @PathVariable UUID widgetId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateWidgetRequest request) {
        Dashboard.Layout layout = layout(request.layout());
        Dashboard dashboard = dashboards.updateWidget(principal(authentication), dashboardId, widgetId,
                parseVersion(ifMatch), new DashboardManagementService.UpdateWidget(request.meterId(),
                        request.title(), request.visualization(), request.field(), request.aggregation(),
                        request.bucketSeconds(), layout, request.sortOrder()));
        return response(dashboard);
    }

    @DeleteMapping("/{dashboardId}/widgets/{widgetId}")
    @PreAuthorize("hasAuthority('PERM_dashboard:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<DashboardResponse> removeWidget(Authentication authentication,
            @PathVariable UUID dashboardId, @PathVariable UUID widgetId,
            @RequestHeader("If-Match") String ifMatch) {
        return response(dashboards.removeWidget(principal(authentication), dashboardId, widgetId,
                parseVersion(ifMatch)));
    }

    private static ResponseEntity<DashboardResponse> response(Dashboard dashboard) {
        List<WidgetResponse> widgets = dashboard.widgets().stream()
                .map(widget -> new WidgetResponse(widget.id(), widget.meterId(), widget.title(),
                        widget.visualization(), widget.field(), widget.aggregation(), widget.bucketSeconds(),
                        widget.layout(), widget.sortOrder(), widget.createdAt(), widget.updatedAt()))
                .toList();
        DashboardResponse body = new DashboardResponse(dashboard.id(), dashboard.organizationId(),
                dashboard.ownerAccountId(), dashboard.code(), dashboard.displayName(),
                dashboard.description().orElse(null), dashboard.defaultZoneId(), dashboard.status(),
                dashboard.version(), dashboard.createdAt(), dashboard.updatedAt(), widgets);
        return ResponseEntity.ok().eTag("\"" + dashboard.version() + "\"").body(body);
    }

    private static DashboardSummaryResponse summary(Dashboard dashboard) {
        return new DashboardSummaryResponse(dashboard.id(), dashboard.organizationId(),
                dashboard.ownerAccountId(), dashboard.code(), dashboard.displayName(),
                dashboard.description().orElse(null), dashboard.defaultZoneId(), dashboard.status(),
                dashboard.version(), dashboard.createdAt(), dashboard.updatedAt());
    }

    private static Dashboard.Layout layout(LayoutRequest request) {
        return new Dashboard.Layout(request.x(), request.y(), request.width(), request.height());
    }

    private static long parseVersion(String value) {
        if (value == null) throw new IllegalArgumentException("If-Match is required");
        String normalized = value.strip();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).strip();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            long version = Long.parseLong(normalized);
            if (version < 0) throw new NumberFormatException("negative");
            return version;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("If-Match must contain a non-negative dashboard version", invalid);
        }
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CreateDashboardRequest(@NotNull UUID organizationId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String displayName,
            @Size(max = 512) String description,
            @Size(max = 64) String defaultZoneId) { }

    public record UpdateDashboardRequest(@NotBlank @Size(max = 128) String displayName,
            @Size(max = 512) String description,
            @NotBlank @Size(max = 64) String defaultZoneId) { }

    public record AddWidgetRequest(@NotNull UUID meterId,
            @NotBlank @Size(max = 128) String title,
            @NotNull Dashboard.Visualization visualization,
            @NotNull TelemetryQuery.ValueField field,
            @NotNull TelemetryQuery.Aggregation aggregation,
            @Min(1) @Max(2678400) long bucketSeconds,
            @NotNull @Valid LayoutRequest layout,
            @Min(0) @Max(10000) int sortOrder) { }

    public record UpdateWidgetRequest(@NotNull UUID meterId,
            @NotBlank @Size(max = 128) String title,
            @NotNull Dashboard.Visualization visualization,
            @NotNull TelemetryQuery.ValueField field,
            @NotNull TelemetryQuery.Aggregation aggregation,
            @Min(1) @Max(2678400) long bucketSeconds,
            @NotNull @Valid LayoutRequest layout,
            @Min(0) @Max(10000) int sortOrder) { }

    public record LayoutRequest(@Min(0) @Max(23) int x,
            @Min(0) @Max(10000) int y,
            @Min(1) @Max(24) int width,
            @Min(1) @Max(100) int height) { }

    public record DashboardResponse(UUID id, UUID organizationId, UUID ownerAccountId,
            String code, String displayName, String description, String defaultZoneId,
            Dashboard.Status status, long version, Instant createdAt, Instant updatedAt,
            List<WidgetResponse> widgets) { }

    public record DashboardSummaryResponse(UUID id, UUID organizationId, UUID ownerAccountId,
            String code, String displayName, String description, String defaultZoneId,
            Dashboard.Status status, long version, Instant createdAt, Instant updatedAt) { }

    public record WidgetResponse(UUID id, UUID meterId, String title,
            Dashboard.Visualization visualization, TelemetryQuery.ValueField field,
            TelemetryQuery.Aggregation aggregation, long bucketSeconds, Dashboard.Layout layout,
            int sortOrder, Instant createdAt, Instant updatedAt) { }
}
