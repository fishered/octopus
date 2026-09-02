package com.accuenergy.octopus.mgmt.infrastructure.persistence.dashboard;

import java.time.Instant;
import java.util.UUID;

public final class DashboardWidgetEntity {
    private UUID id;
    private UUID tenantId;
    private UUID dashboardId;
    private UUID meterId;
    private String title;
    private String visualization;
    private String valueField;
    private String aggregation;
    private long bucketSeconds;
    private String layoutJson;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getDashboardId() { return dashboardId; } public void setDashboardId(UUID value) { dashboardId = value; }
    public UUID getMeterId() { return meterId; } public void setMeterId(UUID value) { meterId = value; }
    public String getTitle() { return title; } public void setTitle(String value) { title = value; }
    public String getVisualization() { return visualization; } public void setVisualization(String value) { visualization = value; }
    public String getValueField() { return valueField; } public void setValueField(String value) { valueField = value; }
    public String getAggregation() { return aggregation; } public void setAggregation(String value) { aggregation = value; }
    public long getBucketSeconds() { return bucketSeconds; } public void setBucketSeconds(long value) { bucketSeconds = value; }
    public String getLayoutJson() { return layoutJson; } public void setLayoutJson(String value) { layoutJson = value; }
    public int getSortOrder() { return sortOrder; } public void setSortOrder(int value) { sortOrder = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
