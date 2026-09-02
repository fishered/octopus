ALTER TABLE resource_grant
    ADD COLUMN created_by_account_id uuid,
    ADD COLUMN revoked_by_account_id uuid,
    ADD COLUMN revoked_at timestamptz,
    ADD COLUMN revoke_reason varchar(500),
    ADD CONSTRAINT ck_resource_grant_effect
        CHECK (effect IN ('ALLOW', 'DENY')),
    ADD CONSTRAINT ck_resource_grant_revocation_complete
        CHECK (
            (revoked_at IS NULL AND revoked_by_account_id IS NULL AND revoke_reason IS NULL)
            OR
            (revoked_at IS NOT NULL AND revoked_by_account_id IS NOT NULL AND revoke_reason IS NOT NULL)
        );

CREATE INDEX ix_resource_grant_active_membership
    ON resource_grant (tenant_id, membership_id, created_at DESC)
    WHERE effect = 'ALLOW' AND revoked_at IS NULL;
