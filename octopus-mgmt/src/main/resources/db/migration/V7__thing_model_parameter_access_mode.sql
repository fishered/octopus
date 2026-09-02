ALTER TABLE thing_model_parameter
    ADD COLUMN access_mode varchar(16) NOT NULL DEFAULT 'READ_ONLY';

ALTER TABLE thing_model_parameter
    ADD CONSTRAINT ck_thing_model_parameter_access_mode
    CHECK (access_mode IN ('READ_ONLY', 'WRITE_ONLY', 'READ_WRITE'));

COMMENT ON COLUMN thing_model_parameter.access_mode IS
    'READ_ONLY telemetry/state, WRITE_ONLY command intent, or READ_WRITE shadow property.';
