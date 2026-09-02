CREATE TABLE device_shadow_projection (
    tenant_id uuid NOT NULL,
    device_id uuid NOT NULL,
    shadow_version bigint NOT NULL,
    reported_state jsonb NOT NULL,
    reported_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    projection_version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, device_id),
    CHECK (shadow_version >= 0),
    CHECK (projection_version >= 0),
    CHECK (jsonb_typeof(reported_state) = 'object')
);
CREATE INDEX ix_device_shadow_projection_received
    ON device_shadow_projection (tenant_id, received_at DESC);

CREATE TABLE device_shadow_inbox (
    tenant_id uuid NOT NULL,
    event_id uuid NOT NULL,
    device_id uuid NOT NULL,
    received_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, event_id)
);
CREATE INDEX ix_device_shadow_inbox_retention ON device_shadow_inbox (received_at);

ALTER TABLE device_shadow_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_shadow_projection FORCE ROW LEVEL SECURITY;
ALTER TABLE device_shadow_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_shadow_inbox FORCE ROW LEVEL SECURITY;

CREATE POLICY device_shadow_projection_tenant_policy ON device_shadow_projection
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY device_shadow_inbox_tenant_policy ON device_shadow_inbox
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON TABLE device_shadow_projection IS
    'Latest tenant-facing reported device state; source of truth remains the device/control event stream.';
COMMENT ON TABLE device_shadow_inbox IS
    'Idempotency ledger for reported shadow events; retain at least as long as Kafka replay.';
