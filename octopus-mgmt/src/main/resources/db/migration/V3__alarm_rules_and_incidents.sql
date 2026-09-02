CREATE TABLE alarm_rule (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    device_id uuid NOT NULL,
    meter_id uuid NOT NULL,
    parameter_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    display_name varchar(128) NOT NULL,
    value_selector varchar(32) NOT NULL,
    comparison varchar(32) NOT NULL,
    trigger_threshold numeric(38, 18) NOT NULL,
    clear_threshold numeric(38, 18) NOT NULL,
    severity varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code),
    CHECK (value_selector IN ('RAW', 'DELTA', 'INTERVAL_ACCUMULATION')),
    CHECK (comparison IN ('GREATER_THAN', 'GREATER_OR_EQUAL', 'LESS_THAN', 'LESS_OR_EQUAL', 'EQUAL', 'NOT_EQUAL')),
    CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CHECK (status IN ('ACTIVE', 'DISABLED')),
    CHECK (version >= 0),
    CHECK (
        (comparison IN ('GREATER_THAN', 'GREATER_OR_EQUAL') AND clear_threshold <= trigger_threshold)
        OR (comparison IN ('LESS_THAN', 'LESS_OR_EQUAL') AND clear_threshold >= trigger_threshold)
        OR (comparison IN ('EQUAL', 'NOT_EQUAL') AND clear_threshold = trigger_threshold)
    )
);
CREATE INDEX ix_alarm_rule_meter_active
    ON alarm_rule (tenant_id, meter_id, parameter_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_alarm_rule_device ON alarm_rule (tenant_id, device_id);

CREATE TABLE alarm_incident (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    rule_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    device_id uuid NOT NULL,
    meter_id uuid NOT NULL,
    parameter_id uuid NOT NULL,
    severity varchar(16) NOT NULL,
    state varchar(16) NOT NULL,
    trigger_value numeric(38, 18) NOT NULL,
    latest_value numeric(38, 18) NOT NULL,
    occurrence_count bigint NOT NULL,
    opened_at timestamptz NOT NULL,
    last_observed_at timestamptz NOT NULL,
    acknowledged_by uuid,
    acknowledged_at timestamptz,
    cleared_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CHECK (state IN ('OPEN', 'ACKNOWLEDGED', 'CLEARED')),
    CHECK (occurrence_count > 0),
    CHECK (version >= 0),
    CHECK ((acknowledged_by IS NULL) = (acknowledged_at IS NULL)),
    CHECK ((state = 'CLEARED' AND cleared_at IS NOT NULL) OR (state <> 'CLEARED' AND cleared_at IS NULL))
);
CREATE UNIQUE INDEX ux_alarm_incident_open_rule
    ON alarm_incident (tenant_id, rule_id, meter_id)
    WHERE state IN ('OPEN', 'ACKNOWLEDGED');
CREATE INDEX ix_alarm_incident_device_time
    ON alarm_incident (tenant_id, device_id, opened_at DESC);
CREATE INDEX ix_alarm_incident_state_time
    ON alarm_incident (tenant_id, state, opened_at DESC);

CREATE TABLE alarm_evaluation_event (
    tenant_id uuid NOT NULL,
    rule_id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, rule_id, source_event_id)
);
CREATE INDEX ix_alarm_evaluation_retention ON alarm_evaluation_event (processed_at);
COMMENT ON TABLE alarm_evaluation_event IS
    'Idempotency ledger for normalized telemetry; prune only after Kafka replay and alarm audit retention windows.';

ALTER TABLE alarm_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_rule FORCE ROW LEVEL SECURITY;
ALTER TABLE alarm_incident ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_incident FORCE ROW LEVEL SECURITY;
ALTER TABLE alarm_evaluation_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_evaluation_event FORCE ROW LEVEL SECURITY;

CREATE POLICY alarm_rule_tenant_policy ON alarm_rule
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY alarm_incident_tenant_policy ON alarm_incident
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY alarm_evaluation_tenant_policy ON alarm_evaluation_event
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
