ALTER TABLE integration_outbox
    ADD COLUMN relay_sequence bigserial NOT NULL;

ALTER TABLE integration_outbox
    ADD CONSTRAINT uq_integration_outbox_relay_sequence UNIQUE (relay_sequence);

DROP INDEX ix_integration_outbox_pending;
CREATE INDEX ix_integration_outbox_pending
    ON integration_outbox (available_at, relay_sequence) WHERE status = 'PENDING';

COMMENT ON COLUMN integration_outbox.relay_sequence IS
    'Cluster-wide insertion order used by the single advisory-locked relay.';
