CREATE TABLE dashboard (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    owner_account_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    display_name varchar(128) NOT NULL,
    description varchar(512),
    default_zone_id varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code),
    CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9_-]*$'),
    CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CHECK (version >= 0),
    CHECK (length(default_zone_id) > 0),
    CHECK (updated_at >= created_at)
);
CREATE INDEX ix_dashboard_organization
    ON dashboard (tenant_id, organization_id, status, updated_at DESC);
CREATE INDEX ix_dashboard_owner
    ON dashboard (tenant_id, owner_account_id, updated_at DESC);

CREATE TABLE dashboard_widget (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    dashboard_id uuid NOT NULL,
    meter_id uuid NOT NULL,
    title varchar(128) NOT NULL,
    visualization varchar(24) NOT NULL,
    value_field varchar(32) NOT NULL,
    aggregation varchar(16) NOT NULL,
    bucket_seconds bigint NOT NULL,
    layout jsonb NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (visualization IN ('LINE', 'BAR', 'SINGLE_VALUE', 'GAUGE')),
    CHECK (value_field IN ('RAW_VALUE', 'DELTA', 'INTERVAL_ACCUMULATION')),
    CHECK (aggregation IN ('MEAN', 'SUM', 'MIN', 'MAX', 'LAST')),
    CHECK (NOT (value_field = 'RAW_VALUE' AND aggregation = 'SUM')),
    CHECK (bucket_seconds BETWEEN 1 AND 2678400),
    CHECK (sort_order BETWEEN 0 AND 10000),
    CHECK (jsonb_typeof(layout) = 'object'),
    CHECK (layout ?& ARRAY['x', 'y', 'width', 'height']),
    CHECK (jsonb_typeof(layout->'x') = 'number'),
    CHECK (jsonb_typeof(layout->'y') = 'number'),
    CHECK (jsonb_typeof(layout->'width') = 'number'),
    CHECK (jsonb_typeof(layout->'height') = 'number'),
    CHECK (updated_at >= created_at)
);
CREATE INDEX ix_dashboard_widget_order
    ON dashboard_widget (tenant_id, dashboard_id, sort_order, id);
CREATE INDEX ix_dashboard_widget_meter
    ON dashboard_widget (tenant_id, meter_id);

ALTER TABLE dashboard ENABLE ROW LEVEL SECURITY;
ALTER TABLE dashboard FORCE ROW LEVEL SECURITY;
ALTER TABLE dashboard_widget ENABLE ROW LEVEL SECURITY;
ALTER TABLE dashboard_widget FORCE ROW LEVEL SECURITY;

CREATE POLICY dashboard_tenant_policy ON dashboard
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY dashboard_widget_tenant_policy ON dashboard_widget
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON TABLE dashboard IS
    'Tenant-owned dashboard metadata. Time-series values remain in InfluxDB and are queried through an allowlisted adapter.';
COMMENT ON TABLE dashboard_widget IS
    'Provider-neutral widget query configuration; executable Flux and arbitrary expressions are intentionally forbidden.';
