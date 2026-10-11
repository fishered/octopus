CREATE TABLE device_onboarding_request (
    request_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_sha256 char(64) NOT NULL,
    status varchar(16) NOT NULL,
    device_id uuid REFERENCES device(id),
    actual_id uuid REFERENCES device_actual(id),
    code varchar(128),
    hardware_serial varchar(200),
    plugin_id varchar(64),
    codec_id varchar(64),
    binding_version bigint,
    device_created_at timestamptz,
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    UNIQUE (tenant_id, requested_by, idempotency_key),
    CHECK (length(idempotency_key) > 0),
    CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CHECK (
        (status = 'PROCESSING' AND device_id IS NULL AND actual_id IS NULL AND code IS NULL
            AND hardware_serial IS NULL AND plugin_id IS NULL AND codec_id IS NULL
            AND binding_version IS NULL AND device_created_at IS NULL AND completed_at IS NULL)
        OR
        (status = 'COMPLETED' AND device_id IS NOT NULL AND actual_id IS NOT NULL AND code IS NOT NULL
            AND hardware_serial IS NOT NULL AND plugin_id IS NOT NULL AND codec_id IS NOT NULL
            AND binding_version IS NOT NULL AND device_created_at IS NOT NULL AND completed_at IS NOT NULL)
    )
);

ALTER TABLE device_onboarding_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_onboarding_request FORCE ROW LEVEL SECURITY;
CREATE POLICY device_onboarding_request_tenant_policy ON device_onboarding_request
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
