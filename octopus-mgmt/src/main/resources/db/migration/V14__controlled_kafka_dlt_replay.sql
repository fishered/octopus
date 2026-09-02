CREATE TABLE kafka_dlt_replay_job (
    id uuid PRIMARY KEY,
    actor_account_id uuid NOT NULL,
    actor_session_id uuid NOT NULL,
    source_dlt_topic varchar(255) NOT NULL,
    source_partition integer NOT NULL,
    source_offset bigint NOT NULL,
    source_timestamp timestamptz NOT NULL,
    source_key_sha256 char(64) NOT NULL,
    payload_size integer NOT NULL,
    destination_topic varchar(255) NOT NULL,
    replay_attempt integer NOT NULL,
    replay_root varchar(512) NOT NULL,
    reason varchar(500) NOT NULL,
    status varchar(24) NOT NULL,
    destination_partition integer,
    destination_offset bigint,
    destination_timestamp timestamptz,
    failure_reason varchar(1000),
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT ux_kafka_dlt_replay_source UNIQUE (source_dlt_topic, source_partition, source_offset),
    CHECK (source_partition >= 0),
    CHECK (source_offset >= 0),
    CHECK (payload_size >= 0),
    CHECK (replay_attempt > 0),
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CHECK (destination_partition IS NULL OR destination_partition >= 0),
    CHECK (destination_offset IS NULL OR destination_offset >= 0),
    CHECK ((status = 'PENDING' AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND completed_at IS NOT NULL)),
    CHECK (status <> 'SUCCEEDED'
        OR (destination_partition IS NOT NULL AND destination_offset IS NOT NULL
            AND destination_timestamp IS NOT NULL)),
    CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL)
);
CREATE INDEX ix_kafka_dlt_replay_created ON kafka_dlt_replay_job (created_at DESC);
CREATE INDEX ix_kafka_dlt_replay_root ON kafka_dlt_replay_job (replay_root, replay_attempt);
COMMENT ON TABLE kafka_dlt_replay_job IS
    'Platform-only immutable source-offset reservation and outcome ledger for controlled Kafka DLT replay';
COMMENT ON COLUMN kafka_dlt_replay_job.source_key_sha256 IS
    'Trace digest only; replay reads the retained Kafka key/value and does not persist message payloads';
