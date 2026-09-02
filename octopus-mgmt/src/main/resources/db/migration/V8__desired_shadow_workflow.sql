ALTER TABLE device_shadow_projection
    ADD COLUMN applied_desired_version bigint;

ALTER TABLE device_shadow_projection
    ADD CONSTRAINT ck_device_shadow_applied_desired_version
    CHECK (applied_desired_version IS NULL OR applied_desired_version > 0);

CREATE TABLE device_desired_shadow_request (
    request_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    device_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    expected_version bigint NOT NULL,
    desired_version bigint NOT NULL,
    desired_state jsonb NOT NULL,
    request_sha256 char(64) NOT NULL,
    command_id uuid NOT NULL UNIQUE,
    status varchar(24) NOT NULL,
    failure_code varchar(128),
    requested_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    applied_at timestamptz,
    status_updated_at timestamptz NOT NULL,
    projection_version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, requested_by, idempotency_key),
    CHECK (expected_version >= 0),
    CHECK (desired_version = expected_version + 1),
    CHECK (jsonb_typeof(desired_state) = 'object'),
    CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (status IN ('REQUESTED', 'DISPATCHED', 'ACKNOWLEDGED', 'EXECUTED',
                      'APPLIED', 'FAILED', 'EXPIRED', 'SUPERSEDED')),
    CHECK (expires_at > requested_at),
    CHECK (projection_version >= 0),
    CHECK ((status = 'FAILED' AND failure_code IS NOT NULL) OR status <> 'FAILED')
);
CREATE INDEX ix_desired_shadow_request_device_version
    ON device_desired_shadow_request (tenant_id, device_id, desired_version DESC);
CREATE INDEX ix_desired_shadow_request_command
    ON device_desired_shadow_request (tenant_id, command_id);

CREATE TABLE device_desired_shadow_current (
    tenant_id uuid NOT NULL,
    device_id uuid NOT NULL,
    desired_version bigint NOT NULL,
    request_id uuid NOT NULL,
    desired_state jsonb NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, device_id),
    CHECK (desired_version > 0),
    CHECK (jsonb_typeof(desired_state) = 'object')
);

ALTER TABLE device_desired_shadow_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_desired_shadow_request FORCE ROW LEVEL SECURITY;
ALTER TABLE device_desired_shadow_current ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_desired_shadow_current FORCE ROW LEVEL SECURITY;

CREATE POLICY desired_shadow_request_tenant_policy ON device_desired_shadow_request
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY desired_shadow_current_tenant_policy ON device_desired_shadow_current
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON TABLE device_desired_shadow_request IS
    'Append-only desired-state intents linked to the reliable device command lifecycle.';
COMMENT ON TABLE device_desired_shadow_current IS
    'Optimistically versioned latest desired-state intent per tenant device.';
