package com.accuenergy.octopus.mgmt.domain.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardTest {
    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");

    @Test
    void addsAllowlistedWidgetAndAdvancesAggregateVersion() {
        Dashboard dashboard = Dashboard.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "energy_ops", "Energy operations", null, "Asia/Shanghai", NOW);
        Dashboard.Widget widget = Dashboard.Widget.create(UUID.randomUUID(), UUID.randomUUID(), "Load",
                Dashboard.Visualization.LINE, TelemetryQuery.ValueField.RAW_VALUE,
                TelemetryQuery.Aggregation.MEAN, 300, new Dashboard.Layout(0, 0, 12, 4), 10, NOW);

        dashboard.addWidget(widget, NOW.plusSeconds(1));

        assertEquals(1, dashboard.version());
        assertEquals(widget.id(), dashboard.widgets().getFirst().id());
        assertEquals(NOW.plusSeconds(1), dashboard.updatedAt());
    }

    @Test
    void rejectsUnsafeQueryAndInvalidGridLayout() {
        assertThrows(IllegalArgumentException.class, () -> Dashboard.Widget.create(UUID.randomUUID(),
                UUID.randomUUID(), "Invalid", Dashboard.Visualization.BAR,
                TelemetryQuery.ValueField.RAW_VALUE, TelemetryQuery.Aggregation.SUM,
                60, new Dashboard.Layout(0, 0, 6, 4), 0, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Dashboard.Layout(20, 0, 8, 4));
    }

    @Test
    void updatesRemovesAndArchivesUnderOneAggregateVersion() {
        Dashboard dashboard = Dashboard.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "operations", "Operations", null, "UTC", NOW);
        UUID widgetId = UUID.randomUUID();
        Dashboard.Widget widget = Dashboard.Widget.create(widgetId, UUID.randomUUID(), "Load",
                Dashboard.Visualization.LINE, TelemetryQuery.ValueField.RAW_VALUE,
                TelemetryQuery.Aggregation.MEAN, 300, new Dashboard.Layout(0, 0, 12, 4), 10, NOW);
        dashboard.addWidget(widget, NOW.plusSeconds(1));

        dashboard.updateMetadata("Operations v2", "Updated", "Europe/London", NOW.plusSeconds(2));
        Dashboard.Widget updated = dashboard.updateWidget(widgetId, widget.meterId(), "Peak load",
                Dashboard.Visualization.GAUGE, TelemetryQuery.ValueField.RAW_VALUE,
                TelemetryQuery.Aggregation.MAX, 60, new Dashboard.Layout(12, 0, 12, 4), 20,
                NOW.plusSeconds(3));
        dashboard.removeWidget(widgetId, NOW.plusSeconds(4));
        assertTrue(dashboard.archive(NOW.plusSeconds(5)));

        assertEquals("Peak load", updated.title());
        assertEquals(5, dashboard.version());
        assertEquals(Dashboard.Status.ARCHIVED, dashboard.status());
        assertEquals(0, dashboard.widgets().size());
        assertThrows(IllegalStateException.class, () -> dashboard.updateMetadata(
                "No", null, "UTC", NOW.plusSeconds(6)));
    }
}
