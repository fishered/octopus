CREATE TABLE device_command_request (
    command_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    device_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    operation varchar(64) NOT NULL,
    payload_sha256 char(64) NOT NULL,
    status varchar(24) NOT NULL,
    failure_code varchar(128),
    requested_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    dispatched_at timestamptz,
    acknowledged_at timestamptz,
    completed_at timestamptz,
    status_updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, requested_by, idempotency_key),
    CHECK (operation ~ '^[A-Za-z][A-Za-z0-9._-]{0,63}$'),
    CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (status IN ('ACCEPTED', 'DISPATCHED', 'ACKNOWLEDGED', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CHECK (expires_at > requested_at),
    CHECK (version >= 0),
    CHECK ((status = 'FAILED' AND failure_code IS NOT NULL) OR status <> 'FAILED')
);
CREATE INDEX ix_device_command_request_device_time
    ON device_command_request (tenant_id, device_id, requested_at DESC);
CREATE INDEX ix_device_command_request_status_time
    ON device_command_request (tenant_id, status, status_updated_at DESC);

CREATE TABLE device_command_status_inbox (
    tenant_id uuid NOT NULL,
    event_id uuid NOT NULL,
    command_id uuid NOT NULL,
    received_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, event_id)
);
CREATE INDEX ix_device_command_status_inbox_retention
    ON device_command_status_inbox (received_at);

ALTER TABLE device_command_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_command_request FORCE ROW LEVEL SECURITY;
ALTER TABLE device_command_status_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_command_status_inbox FORCE ROW LEVEL SECURITY;

CREATE POLICY device_command_request_tenant_policy ON device_command_request
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY device_command_status_inbox_tenant_policy ON device_command_status_inbox
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON TABLE device_command_request IS
    'Tenant-facing command request/audit projection; octopus-control owns execution.';
COMMENT ON TABLE device_command_status_inbox IS
    'Idempotency ledger for command status events; retain for at least the Kafka replay window.';
