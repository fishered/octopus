ALTER TABLE ca_device_identity ADD COLUMN device_id uuid;

-- Existing claimed identities predate the explicit logical-device binding. Preserve their
-- behavior during upgrade; operators can reconcile these generated bindings afterwards.
UPDATE ca_device_identity
   SET device_id = identity_id
 WHERE tenant_id IS NOT NULL;

ALTER TABLE ca_device_identity
    ADD CONSTRAINT ck_ca_identity_claim_binding
    CHECK ((tenant_id IS NULL AND device_id IS NULL) OR (tenant_id IS NOT NULL AND device_id IS NOT NULL));

CREATE UNIQUE INDEX ux_ca_identity_tenant_device
    ON ca_device_identity (tenant_id, device_id)
    WHERE tenant_id IS NOT NULL AND device_id IS NOT NULL;
