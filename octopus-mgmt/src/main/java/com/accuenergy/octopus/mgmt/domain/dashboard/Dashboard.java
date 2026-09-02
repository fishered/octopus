package com.accuenergy.octopus.mgmt.domain.dashboard;

import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Tenant-owned dashboard aggregate. Widget queries are allowlisted and never contain executable Flux. */
public final class Dashboard {
    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID ownerAccountId;
    private final String code;
    private String displayName;
    private String description;
    private String defaultZoneId;
    private Status status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<Widget> widgets;

    private Dashboard(UUID id, UUID tenantId, UUID organizationId, UUID ownerAccountId,
            String code, String displayName, String description, String defaultZoneId,
            Status status, long version, Instant createdAt, Instant updatedAt, List<Widget> widgets) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.ownerAccountId = Objects.requireNonNull(ownerAccountId, "ownerAccountId");
        this.code = requireCode(code);
        this.displayName = requireText(displayName, "displayName", 128);
        this.description = optionalText(description, "description", 512);
        this.defaultZoneId = requireZone(defaultZoneId);
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.widgets = new ArrayList<>(Objects.requireNonNull(widgets, "widgets"));
    }

    public static Dashboard create(UUID id, UUID tenantId, UUID organizationId, UUID ownerAccountId,
            String code, String displayName, String description, String defaultZoneId, Instant now) {
        return new Dashboard(id, tenantId, organizationId, ownerAccountId, code, displayName,
                description, defaultZoneId, Status.ACTIVE, 0, now, now, List.of());
    }

    public static Dashboard restore(UUID id, UUID tenantId, UUID organizationId, UUID ownerAccountId,
            String code, String displayName, String description, String defaultZoneId,
            Status status, long version, Instant createdAt, Instant updatedAt, List<Widget> widgets) {
        return new Dashboard(id, tenantId, organizationId, ownerAccountId, code, displayName,
                description, defaultZoneId, status, version, createdAt, updatedAt, widgets);
    }

    public void addWidget(Widget widget, Instant now) {
        Objects.requireNonNull(widget, "widget");
        if (status != Status.ACTIVE) throw new IllegalStateException("Archived dashboards cannot be changed");
        if (widgets.stream().anyMatch(existing -> existing.id().equals(widget.id()))) {
            throw new IllegalArgumentException("Widget already exists");
        }
        widgets.add(widget);
        version++;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void updateMetadata(String displayName, String description, String defaultZoneId, Instant now) {
        requireActive();
        this.displayName = requireText(displayName, "displayName", 128);
        this.description = optionalText(description, "description", 512);
        this.defaultZoneId = requireZone(defaultZoneId);
        advance(now);
    }

    public boolean archive(Instant now) {
        if (status == Status.ARCHIVED) return false;
        status = Status.ARCHIVED;
        advance(now);
        return true;
    }

    public Widget updateWidget(UUID widgetId, UUID meterId, String title, Visualization visualization,
            TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
            long bucketSeconds, Layout layout, int sortOrder, Instant now) {
        requireActive();
        for (int index = 0; index < widgets.size(); index++) {
            Widget existing = widgets.get(index);
            if (existing.id().equals(widgetId)) {
                Widget updated = Widget.restore(widgetId, meterId, title, visualization, field, aggregation,
                        bucketSeconds, layout, sortOrder, existing.createdAt(), Objects.requireNonNull(now, "now"));
                widgets.set(index, updated);
                advance(now);
                return updated;
            }
        }
        throw new IllegalArgumentException("Unknown dashboard widget");
    }

    public Widget removeWidget(UUID widgetId, Instant now) {
        requireActive();
        for (int index = 0; index < widgets.size(); index++) {
            Widget existing = widgets.get(index);
            if (existing.id().equals(widgetId)) {
                widgets.remove(index);
                advance(now);
                return existing;
            }
        }
        throw new IllegalArgumentException("Unknown dashboard widget");
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public UUID ownerAccountId() { return ownerAccountId; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public Optional<String> description() { return Optional.ofNullable(description); }
    public String defaultZoneId() { return defaultZoneId; }
    public Status status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<Widget> widgets() { return List.copyOf(widgets); }

    public enum Status { ACTIVE, ARCHIVED }

    public static final class Widget {
        private static final long MAX_BUCKET_SECONDS = 31L * 24 * 60 * 60;
        private final UUID id;
        private final UUID meterId;
        private final String title;
        private final Visualization visualization;
        private final TelemetryQuery.ValueField field;
        private final TelemetryQuery.Aggregation aggregation;
        private final long bucketSeconds;
        private final Layout layout;
        private final int sortOrder;
        private final Instant createdAt;
        private final Instant updatedAt;

        private Widget(UUID id, UUID meterId, String title, Visualization visualization,
                TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
                long bucketSeconds, Layout layout, int sortOrder, Instant createdAt, Instant updatedAt) {
            this.id = Objects.requireNonNull(id, "id");
            this.meterId = Objects.requireNonNull(meterId, "meterId");
            this.title = requireText(title, "title", 128);
            this.visualization = Objects.requireNonNull(visualization, "visualization");
            this.field = Objects.requireNonNull(field, "field");
            this.aggregation = Objects.requireNonNull(aggregation, "aggregation");
            if (bucketSeconds < 1 || bucketSeconds > MAX_BUCKET_SECONDS) {
                throw new IllegalArgumentException("bucketSeconds must be between 1 second and 31 days");
            }
            if (field == TelemetryQuery.ValueField.RAW_VALUE && aggregation == TelemetryQuery.Aggregation.SUM) {
                throw new IllegalArgumentException("SUM is not valid for raw readings");
            }
            this.bucketSeconds = bucketSeconds;
            this.layout = Objects.requireNonNull(layout, "layout");
            if (sortOrder < 0 || sortOrder > 10_000) {
                throw new IllegalArgumentException("sortOrder must be between 0 and 10000");
            }
            this.sortOrder = sortOrder;
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
            this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        }

        public static Widget create(UUID id, UUID meterId, String title, Visualization visualization,
                TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
                long bucketSeconds, Layout layout, int sortOrder, Instant now) {
            return new Widget(id, meterId, title, visualization, field, aggregation,
                    bucketSeconds, layout, sortOrder, now, now);
        }

        public static Widget restore(UUID id, UUID meterId, String title, Visualization visualization,
                TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
                long bucketSeconds, Layout layout, int sortOrder, Instant createdAt, Instant updatedAt) {
            return new Widget(id, meterId, title, visualization, field, aggregation,
                    bucketSeconds, layout, sortOrder, createdAt, updatedAt);
        }

        public UUID id() { return id; }
        public UUID meterId() { return meterId; }
        public String title() { return title; }
        public Visualization visualization() { return visualization; }
        public TelemetryQuery.ValueField field() { return field; }
        public TelemetryQuery.Aggregation aggregation() { return aggregation; }
        public long bucketSeconds() { return bucketSeconds; }
        public Layout layout() { return layout; }
        public int sortOrder() { return sortOrder; }
        public Instant createdAt() { return createdAt; }
        public Instant updatedAt() { return updatedAt; }
    }

    public record Layout(int x, int y, int width, int height) {
        public Layout {
            if (x < 0 || x > 23) throw new IllegalArgumentException("layout.x must be between 0 and 23");
            if (y < 0 || y > 10_000) throw new IllegalArgumentException("layout.y must be between 0 and 10000");
            if (width < 1 || width > 24 || x + width > 24) {
                throw new IllegalArgumentException("layout.width must fit within the 24-column grid");
            }
            if (height < 1 || height > 100) {
                throw new IllegalArgumentException("layout.height must be between 1 and 100");
            }
        }
    }

    public enum Visualization { LINE, BAR, SINGLE_VALUE, GAUGE }

    private void requireActive() {
        if (status != Status.ACTIVE) throw new IllegalStateException("Archived dashboards cannot be changed");
    }

    private void advance(Instant now) {
        version++;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    private static String requireCode(String value) {
        String code = requireText(value, "code", 64);
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("code contains unsupported characters");
        }
        return code;
    }

    private static String requireZone(String value) {
        String zone = requireText(value, "defaultZoneId", 64);
        ZoneId.of(zone);
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new IllegalArgumentException("defaultZoneId must be an IANA region ID");
        }
        return zone;
    }

    private static String optionalText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, name, maximumLength);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}
