CREATE TABLE device_connector_binding (
    tenant_id uuid NOT NULL, device_id uuid NOT NULL, plugin_id varchar(64) NOT NULL,
    codec_id varchar(64) NOT NULL, config_ref varchar(255), status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, device_id),
    CHECK (plugin_id ~ '^[a-z][a-z0-9._-]{0,63}$'), CHECK (codec_id ~ '^[a-z][a-z0-9._-]{0,63}$'),
    CHECK (status IN ('ACTIVE', 'DISABLED')), CHECK (version >= 1)
);
CREATE INDEX ix_device_connector_binding_device ON device_connector_binding (tenant_id, device_id);
ALTER TABLE device_connector_binding ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_connector_binding FORCE ROW LEVEL SECURITY;
CREATE POLICY device_connector_binding_tenant_policy ON device_connector_binding
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
