CREATE TABLE device_command (
    command_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    device_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    operation varchar(64) NOT NULL,
    payload bytea NOT NULL,
    status varchar(24) NOT NULL,
    failure_code varchar(128),
    requested_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    dispatched_at timestamptz,
    acknowledged_at timestamptz,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CHECK (operation ~ '^[A-Za-z][A-Za-z0-9._-]{0,63}$'),
    CHECK (octet_length(payload) <= 65536),
    CHECK (status IN ('ACCEPTED', 'DISPATCHED', 'ACKNOWLEDGED', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CHECK (expires_at > requested_at),
    CHECK (version >= 0),
    CHECK ((status = 'FAILED' AND failure_code IS NOT NULL) OR status <> 'FAILED')
);
CREATE INDEX ix_device_command_tenant_device_time
    ON device_command (tenant_id, device_id, requested_at DESC);
CREATE INDEX ix_device_command_tenant_status
    ON device_command (tenant_id, status, expires_at);

CREATE TABLE command_status_outbox (
    id uuid PRIMARY KEY,
    relay_sequence bigserial NOT NULL UNIQUE,
    tenant_id uuid NOT NULL,
    topic varchar(255) NOT NULL,
    partition_key varchar(255) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(16) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error varchar(1000),
    CHECK (status IN ('PENDING', 'PUBLISHED')),
    CHECK (attempts >= 0)
);
CREATE INDEX ix_command_status_outbox_pending
    ON command_status_outbox (available_at, relay_sequence) WHERE status = 'PENDING';
COMMENT ON TABLE command_status_outbox IS
    'Internal cross-tenant relay table; never exposed through tenant APIs.';

ALTER TABLE device_command ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_command FORCE ROW LEVEL SECURITY;
CREATE POLICY device_command_tenant_policy ON device_command
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
