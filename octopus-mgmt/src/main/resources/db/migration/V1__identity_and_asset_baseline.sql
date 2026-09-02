CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE tenant (
    id uuid PRIMARY KEY,
    code varchar(64) NOT NULL UNIQUE,
    display_name varchar(200) NOT NULL,
    status varchar(24) NOT NULL,
    home_region varchar(64) NOT NULL,
    default_zone_id varchar(64) NOT NULL,
    default_locale varchar(35) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
COMMENT ON TABLE tenant IS 'SaaS subscription, quota, residency, and isolation boundary';

CREATE TABLE organization (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    parent_id uuid,
    path varchar(2000) NOT NULL,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    zone_id varchar(64),
    status varchar(24) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code),
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    CHECK (path LIKE '%/' || id::text)
);
CREATE INDEX ix_organization_tenant_parent ON organization (tenant_id, parent_id);
CREATE INDEX ix_organization_tenant_path ON organization (tenant_id, path text_pattern_ops);
COMMENT ON TABLE organization IS 'Tenant-owned operational hierarchy; parent relation is application-enforced';

CREATE TABLE account (
    id uuid PRIMARY KEY,
    login_name varchar(128) NOT NULL,
    email varchar(320),
    phone_e164 varchar(20),
    password_hash varchar(512) NOT NULL,
    password_algorithm varchar(32) NOT NULL,
    status varchar(24) NOT NULL,
    session_generation bigint NOT NULL DEFAULT 0,
    authorization_generation bigint NOT NULL DEFAULT 0,
    password_changed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (login_name),
    UNIQUE (email)
);
CREATE UNIQUE INDEX ux_account_login_name_normalized ON account (lower(login_name));
CREATE UNIQUE INDEX ux_account_email_normalized ON account (lower(email)) WHERE email IS NOT NULL;
COMMENT ON TABLE account IS 'Global login identity; tenant access is provided by memberships';

CREATE TABLE account_login_security (
    account_id uuid PRIMARY KEY,
    failed_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamptz,
    last_failed_at timestamptz,
    updated_at timestamptz NOT NULL
);
COMMENT ON TABLE account_login_security IS 'Server-side login throttling and lock state';

CREATE TABLE account_membership (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    account_id uuid NOT NULL,
    status varchar(24) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, organization_id, account_id)
);
CREATE INDEX ix_membership_tenant_account ON account_membership (tenant_id, account_id);

CREATE TABLE account_tenant_security_state (
    tenant_id uuid NOT NULL,
    account_id uuid NOT NULL,
    authorization_generation bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, account_id)
);
CREATE INDEX ix_account_tenant_security_account ON account_tenant_security_state (account_id, tenant_id);

CREATE TABLE mfa_factor (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    factor_type varchar(24) NOT NULL,
    encrypted_secret bytea,
    key_version varchar(64),
    status varchar(24) NOT NULL,
    last_accepted_counter bigint NOT NULL DEFAULT -1,
    verified_at timestamptz,
    created_at timestamptz NOT NULL
);
CREATE INDEX ix_mfa_factor_account ON mfa_factor (account_id, status);
CREATE UNIQUE INDEX ux_mfa_active_totp ON mfa_factor (account_id)
    WHERE factor_type = 'TOTP' AND status = 'ACTIVE';
COMMENT ON COLUMN mfa_factor.encrypted_secret IS 'Envelope-encrypted TOTP secret; recovery codes are stored separately as hashes';

CREATE TABLE auth_session (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    tenant_id uuid,
    token_family_id uuid NOT NULL,
    refresh_generation bigint NOT NULL DEFAULT 0,
    account_generation bigint NOT NULL,
    authorization_generation bigint NOT NULL DEFAULT 0,
    refresh_token_hash varchar(512) NOT NULL,
    authentication_level varchar(24) NOT NULL,
    status varchar(24) NOT NULL,
    idle_expires_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoke_reason varchar(200),
    created_at timestamptz NOT NULL,
    UNIQUE (refresh_token_hash)
);
CREATE INDEX ix_auth_session_account_status ON auth_session (account_id, status);
CREATE INDEX ix_auth_session_tenant_status ON auth_session (tenant_id, status);

CREATE TABLE role (
    id uuid PRIMARY KEY,
    tenant_id uuid,
    organization_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    role_type varchar(32) NOT NULL,
    status varchar(24) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (role_type IN ('PLATFORM_ADMIN', 'ORGANIZATION_ADMIN', 'OPERATOR')),
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    CHECK ((role_type = 'PLATFORM_ADMIN' AND tenant_id IS NULL)
           OR (role_type <> 'PLATFORM_ADMIN' AND tenant_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_role_platform_code ON role (code) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX ux_role_tenant_code ON role (tenant_id, code) WHERE tenant_id IS NOT NULL;

CREATE TABLE account_platform_role (
    account_id uuid NOT NULL,
    role_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (account_id, role_id)
);

CREATE TABLE permission (
    id uuid PRIMARY KEY,
    code varchar(128) NOT NULL UNIQUE,
    resource_type varchar(64) NOT NULL,
    action varchar(64) NOT NULL,
    description varchar(500) NOT NULL
);
COMMENT ON TABLE permission IS 'Global action vocabulary; menu visibility does not confer permission';

CREATE TABLE role_permission (
    tenant_id uuid,
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permission_tenant ON role_permission (tenant_id, role_id);

CREATE TABLE membership_role (
    tenant_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    role_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (membership_id, role_id)
);
CREATE INDEX ix_membership_role_tenant ON membership_role (tenant_id, membership_id);

CREATE TABLE resource_grant (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    resource_type varchar(64) NOT NULL,
    resource_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    effect varchar(8) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, membership_id, resource_type, resource_id, action)
);
CREATE INDEX ix_resource_grant_lookup ON resource_grant (tenant_id, membership_id, resource_type, action, resource_id);

CREATE TABLE menu (
    id uuid PRIMARY KEY,
    parent_id uuid,
    code varchar(64) NOT NULL UNIQUE,
    route varchar(300),
    required_permission_code varchar(128),
    sort_order integer NOT NULL,
    status varchar(24) NOT NULL
);
COMMENT ON TABLE menu IS 'Presentation navigation; required_permission_code filters visibility only';

CREATE TABLE facility (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    parent_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    facility_type varchar(64) NOT NULL,
    zone_id varchar(64),
    geometry geometry(Geometry, 4326),
    status varchar(24) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code),
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    CHECK (geometry IS NULL OR ST_IsValid(geometry)),
    CHECK (geometry IS NULL OR (
        ST_XMin(Box2D(geometry)) >= -180 AND ST_XMax(Box2D(geometry)) <= 180
        AND ST_YMin(Box2D(geometry)) >= -90 AND ST_YMax(Box2D(geometry)) <= 90
    ))
);
CREATE INDEX ix_facility_tenant_org ON facility (tenant_id, organization_id);
CREATE INDEX ix_facility_geometry ON facility USING gist (geometry);

CREATE TABLE device_type (
    id uuid PRIMARY KEY,
    tenant_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    capabilities jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(24) NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_device_type_global_code ON device_type (code) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX ux_device_type_tenant_code ON device_type (tenant_id, code) WHERE tenant_id IS NOT NULL;

CREATE TABLE thing_model (
    id uuid PRIMARY KEY,
    tenant_id uuid,
    device_type_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    model_version bigint NOT NULL,
    schema_document jsonb NOT NULL,
    status varchar(24) NOT NULL,
    published_at timestamptz,
    created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_thing_model_global_version ON thing_model (code, model_version) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX ux_thing_model_tenant_version ON thing_model (tenant_id, code, model_version) WHERE tenant_id IS NOT NULL;

CREATE TABLE unit_catalog (
    id uuid PRIMARY KEY,
    code varchar(64) NOT NULL UNIQUE,
    symbol varchar(32) NOT NULL,
    dimension varchar(64) NOT NULL,
    scale numeric(38, 18) NOT NULL DEFAULT 1,
    offset_value numeric(38, 18) NOT NULL DEFAULT 0,
    description varchar(500),
    CHECK (scale > 0)
);
COMMENT ON TABLE unit_catalog IS 'Global UCUM-compatible unit vocabulary';

CREATE TABLE parameter_definition (
    id uuid PRIMARY KEY,
    tenant_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    quantity_kind varchar(64) NOT NULL,
    value_semantics varchar(32) NOT NULL,
    data_type varchar(32) NOT NULL,
    canonical_unit_id uuid,
    decimal_scale integer,
    status varchar(24) NOT NULL,
    CHECK (value_semantics IN ('INSTANTANEOUS', 'CUMULATIVE', 'INTERVAL_DELTA', 'NET')),
    CHECK (data_type IN ('DECIMAL', 'INTEGER', 'BOOLEAN', 'STRING')),
    CHECK (decimal_scale IS NULL OR decimal_scale BETWEEN 0 AND 18),
    CHECK (
        (data_type IN ('DECIMAL', 'INTEGER') AND canonical_unit_id IS NOT NULL)
        OR (data_type IN ('BOOLEAN', 'STRING') AND canonical_unit_id IS NULL AND decimal_scale IS NULL)
    )
);
CREATE UNIQUE INDEX ux_parameter_global_code ON parameter_definition (code) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX ux_parameter_tenant_code ON parameter_definition (tenant_id, code) WHERE tenant_id IS NOT NULL;
COMMENT ON COLUMN parameter_definition.value_semantics IS 'INSTANTANEOUS, CUMULATIVE, INTERVAL_DELTA, or NET';

CREATE TABLE thing_model_parameter (
    tenant_id uuid,
    thing_model_id uuid NOT NULL,
    parameter_id uuid NOT NULL,
    unit_id uuid NOT NULL,
    required boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    PRIMARY KEY (thing_model_id, parameter_id),
    UNIQUE (thing_model_id, sort_order),
    CHECK (sort_order >= 0)
);
CREATE INDEX ix_thing_model_parameter_tenant ON thing_model_parameter (tenant_id, thing_model_id);
COMMENT ON TABLE thing_model_parameter IS 'Version-pinned parameters and source units for an immutable thing model version';

CREATE TABLE device (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    facility_id uuid,
    device_type_id uuid NOT NULL,
    thing_model_id uuid NOT NULL,
    model_version bigint NOT NULL,
    code varchar(128) NOT NULL,
    display_name varchar(200) NOT NULL,
    status varchar(24) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code)
);
CREATE INDEX ix_device_tenant_org ON device (tenant_id, organization_id);
CREATE INDEX ix_device_tenant_facility ON device (tenant_id, facility_id);

CREATE TABLE device_actual (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    device_id uuid NOT NULL,
    hardware_serial varchar(200) NOT NULL,
    manufacturer varchar(200),
    firmware_version varchar(100),
    certificate_id uuid,
    connectivity_status varchar(24) NOT NULL,
    last_seen_at timestamptz,
    commissioned_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, hardware_serial),
    UNIQUE (tenant_id, device_id),
    CHECK (connectivity_status IN ('NEVER_SEEN', 'ONLINE', 'OFFLINE', 'DEGRADED')),
    CHECK (
        (connectivity_status = 'NEVER_SEEN' AND last_seen_at IS NULL)
        OR (connectivity_status <> 'NEVER_SEEN' AND last_seen_at IS NOT NULL)
    )
);
CREATE INDEX ix_device_actual_certificate ON device_actual (tenant_id, certificate_id);
CREATE UNIQUE INDEX ux_device_actual_certificate
    ON device_actual (tenant_id, certificate_id) WHERE certificate_id IS NOT NULL;

CREATE TABLE meter (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    device_id uuid NOT NULL,
    facility_id uuid,
    parameter_id uuid NOT NULL,
    unit_id uuid NOT NULL,
    code varchar(128) NOT NULL,
    display_name varchar(200) NOT NULL,
    meter_kind varchar(32) NOT NULL,
    calculation_expression jsonb,
    rollover_modulus numeric(38, 18),
    decimal_scale integer NOT NULL,
    status varchar(24) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code),
    CHECK (meter_kind IN ('STANDARD', 'CALCULATED', 'BILLING', 'GATEWAY')),
    CHECK (decimal_scale BETWEEN 0 AND 18),
    CHECK (rollover_modulus IS NULL OR rollover_modulus > 0),
    CHECK (
        (meter_kind = 'CALCULATED' AND calculation_expression IS NOT NULL)
        OR (meter_kind <> 'CALCULATED' AND calculation_expression IS NULL)
    )
);
CREATE INDEX ix_meter_tenant_device ON meter (tenant_id, device_id);
CREATE INDEX ix_meter_tenant_facility ON meter (tenant_id, facility_id);
COMMENT ON COLUMN meter.meter_kind IS 'STANDARD, CALCULATED, BILLING, or GATEWAY';

CREATE TABLE security_audit_event (
    id uuid PRIMARY KEY,
    tenant_id uuid,
    actor_account_id uuid,
    session_id uuid,
    event_type varchar(64) NOT NULL,
    resource_type varchar(64),
    resource_id uuid,
    outcome varchar(24) NOT NULL,
    reason varchar(500),
    source_address inet,
    occurred_at timestamptz NOT NULL,
    details jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_security_audit_tenant_time ON security_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_security_audit_actor_time ON security_audit_event (actor_account_id, occurred_at DESC);

CREATE TABLE integration_outbox (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    topic varchar(255) NOT NULL,
    partition_key varchar(255) NOT NULL,
    event_type varchar(128) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(24) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error varchar(1000),
    CHECK (status IN ('PENDING', 'PUBLISHED')),
    CHECK (attempts >= 0)
);
CREATE INDEX ix_integration_outbox_pending
    ON integration_outbox (available_at, occurred_at) WHERE status = 'PENDING';
COMMENT ON TABLE integration_outbox IS 'Internal cross-tenant transactional outbox; never exposed through tenant APIs';

-- Defense in depth. The application sets app.tenant_id per transaction after authentication.
ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization FORCE ROW LEVEL SECURITY;
ALTER TABLE account_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_membership FORCE ROW LEVEL SECURITY;
ALTER TABLE role ENABLE ROW LEVEL SECURITY;
ALTER TABLE role FORCE ROW LEVEL SECURITY;
ALTER TABLE role_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permission FORCE ROW LEVEL SECURITY;
ALTER TABLE membership_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_role FORCE ROW LEVEL SECURITY;
ALTER TABLE resource_grant ENABLE ROW LEVEL SECURITY;
ALTER TABLE resource_grant FORCE ROW LEVEL SECURITY;
ALTER TABLE facility ENABLE ROW LEVEL SECURITY;
ALTER TABLE facility FORCE ROW LEVEL SECURITY;
ALTER TABLE device ENABLE ROW LEVEL SECURITY;
ALTER TABLE device FORCE ROW LEVEL SECURITY;
ALTER TABLE device_actual ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_actual FORCE ROW LEVEL SECURITY;
ALTER TABLE meter ENABLE ROW LEVEL SECURITY;
ALTER TABLE meter FORCE ROW LEVEL SECURITY;
ALTER TABLE device_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_type FORCE ROW LEVEL SECURITY;
ALTER TABLE thing_model ENABLE ROW LEVEL SECURITY;
ALTER TABLE thing_model FORCE ROW LEVEL SECURITY;
ALTER TABLE parameter_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE parameter_definition FORCE ROW LEVEL SECURITY;
ALTER TABLE thing_model_parameter ENABLE ROW LEVEL SECURITY;
ALTER TABLE thing_model_parameter FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_tenant_policy ON organization
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY membership_tenant_policy ON account_membership
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY role_tenant_policy ON role
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY role_permission_tenant_policy ON role_permission
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY membership_role_tenant_policy ON membership_role
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY resource_grant_tenant_policy ON resource_grant
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY facility_tenant_policy ON facility
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY device_tenant_policy ON device
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY device_actual_tenant_policy ON device_actual
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY meter_tenant_policy ON meter
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY device_type_tenant_policy ON device_type
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY thing_model_tenant_policy ON thing_model
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY parameter_definition_tenant_policy ON parameter_definition
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY thing_model_parameter_tenant_policy ON thing_model_parameter
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
