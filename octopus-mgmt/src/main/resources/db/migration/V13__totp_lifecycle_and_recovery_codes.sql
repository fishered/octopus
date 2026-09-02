ALTER TABLE mfa_factor ADD COLUMN expires_at timestamptz;
ALTER TABLE mfa_factor ADD COLUMN updated_at timestamptz;
ALTER TABLE mfa_factor ADD COLUMN revoked_at timestamptz;
ALTER TABLE mfa_factor ADD COLUMN revoke_reason varchar(500);

UPDATE mfa_factor SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE mfa_factor ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX ux_mfa_pending_totp ON mfa_factor (account_id)
    WHERE factor_type = 'TOTP' AND status = 'PENDING';

CREATE TABLE mfa_recovery_code (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    factor_id uuid NOT NULL,
    code_hash varchar(512) NOT NULL,
    status varchar(24) NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_mfa_recovery_factor_status ON mfa_recovery_code (factor_id, status);
CREATE INDEX ix_mfa_recovery_account_status ON mfa_recovery_code (account_id, status);
COMMENT ON COLUMN mfa_recovery_code.code_hash IS 'Argon2id hash; raw recovery codes are returned once at TOTP activation and never persisted';
