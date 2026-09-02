CREATE TABLE ca_device_identity (
    identity_id uuid PRIMARY KEY,
    tenant_id uuid,
    hardware_serial varchar(200) NOT NULL UNIQUE,
    manufacturer varchar(200) NOT NULL,
    model_code varchar(128) NOT NULL,
    batch_code varchar(128) NOT NULL,
    bootstrap_public_key_fingerprint varchar(128) NOT NULL,
    status varchar(32) NOT NULL,
    operational_certificate_serial varchar(200),
    certificate_expires_at timestamptz,
    claimed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (status IN ('MANUFACTURED', 'BOOTSTRAP_READY', 'CLAIMED', 'ACTIVE', 'REVOKED', 'DECOMMISSIONED')),
    CHECK (
        (status IN ('MANUFACTURED', 'BOOTSTRAP_READY') AND tenant_id IS NULL AND claimed_at IS NULL)
        OR (status IN ('CLAIMED', 'ACTIVE', 'REVOKED', 'DECOMMISSIONED')
            AND tenant_id IS NOT NULL AND claimed_at IS NOT NULL)
    ),
    CHECK (status <> 'ACTIVE' OR
           (operational_certificate_serial IS NOT NULL AND certificate_expires_at IS NOT NULL))
);
CREATE INDEX ix_ca_identity_tenant_status ON ca_device_identity (tenant_id, status);
CREATE INDEX ix_ca_identity_batch ON ca_device_identity (manufacturer, batch_code);

CREATE TABLE ca_bootstrap_credential (
    identity_id uuid PRIMARY KEY,
    token_hash varchar(128) NOT NULL UNIQUE,
    status varchar(16) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL,
    CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED')),
    CHECK ((status = 'USED' AND used_at IS NOT NULL) OR status <> 'USED')
);
CREATE INDEX ix_ca_bootstrap_expiry ON ca_bootstrap_credential (status, expires_at);

CREATE TABLE ca_operational_certificate (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    identity_id uuid NOT NULL,
    serial_number varchar(200) NOT NULL UNIQUE,
    certificate_chain bytea NOT NULL,
    csr_fingerprint varchar(64) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    status varchar(24) NOT NULL,
    not_before timestamptz NOT NULL,
    not_after timestamptz NOT NULL,
    issued_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoke_reason varchar(500),
    superseded_at timestamptz,
    UNIQUE (tenant_id, identity_id, idempotency_key),
    CHECK (status IN ('ACTIVE', 'REVOKED', 'SUPERSEDED', 'EXPIRED')),
    CHECK (not_after > not_before),
    CHECK ((status = 'REVOKED' AND revoked_at IS NOT NULL) OR status <> 'REVOKED'),
    CHECK ((status = 'SUPERSEDED' AND superseded_at IS NOT NULL) OR status <> 'SUPERSEDED')
);
CREATE INDEX ix_ca_certificate_tenant_identity ON ca_operational_certificate (tenant_id, identity_id, issued_at DESC);
CREATE INDEX ix_ca_certificate_expiry ON ca_operational_certificate (status, not_after);

ALTER TABLE ca_device_identity ENABLE ROW LEVEL SECURITY;
ALTER TABLE ca_device_identity FORCE ROW LEVEL SECURITY;
ALTER TABLE ca_bootstrap_credential ENABLE ROW LEVEL SECURITY;
ALTER TABLE ca_bootstrap_credential FORCE ROW LEVEL SECURITY;
ALTER TABLE ca_operational_certificate ENABLE ROW LEVEL SECURITY;
ALTER TABLE ca_operational_certificate FORCE ROW LEVEL SECURITY;

CREATE POLICY ca_identity_scope_policy ON ca_device_identity
    USING (current_setting('app.platform_scope', true) = 'true'
           OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (current_setting('app.platform_scope', true) = 'true'
                OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY ca_bootstrap_platform_policy ON ca_bootstrap_credential
    USING (current_setting('app.platform_scope', true) = 'true')
    WITH CHECK (current_setting('app.platform_scope', true) = 'true');

CREATE POLICY ca_certificate_scope_policy ON ca_operational_certificate
    USING (current_setting('app.platform_scope', true) = 'true'
           OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (current_setting('app.platform_scope', true) = 'true'
                OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
