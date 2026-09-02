ALTER TABLE device_type
    ADD CONSTRAINT ck_device_type_capabilities_object
        CHECK (jsonb_typeof(capabilities) = 'object'),
    ADD CONSTRAINT ck_device_type_status
        CHECK (status IN ('ACTIVE', 'RETIRED'));
