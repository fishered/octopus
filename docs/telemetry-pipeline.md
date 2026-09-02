# Telemetry reliability and ordering

## Flow

```text
device --MQTT QoS 1/2--> control/edge adapter --durable publish--> Kafka raw
  --> collect validate/dedupe/reorder/calculate --> InfluxDB + Kafka normalized
  --> mgmt alarm evaluation --> PostgreSQL alarm incident
  --> commit Kafka offsets after each durable stage
  --> quarantine/DLT for deterministic bad records
```

MQTT acknowledgement is sent only after a durable handoff. For cloud-managed MQTT, the
adapter uses the provider's durable rule/bridge to Kafka-equivalent ingress. Local disk
spooling is required at disconnected edge sites.

The current broker adapter uses a persistent MQTT 3.1.1 session, QoS 1 subscription, and
manual acknowledgements. It calls the broker completion API only after Kafka acknowledges
either the raw telemetry record or its durable quarantine envelope. Kafka failures leave
the MQTT message unacknowledged for broker redelivery. The control service stamps
`receivedAt`, rejects retained/QoS 0 telemetry from the raw stream, and verifies that the
tenant/device IDs in the topic match the signed payload contract.

Kafka keys are stable per ordering domain: normally `{tenantId}:{deviceId}:{meterId}`.
This guarantees order only inside a partition. Producers enable idempotence, `acks=all`,
and an appropriate in-flight limit; topics use replication factor at least 3 and
`min.insync.replicas` at least 2. Events include `eventId`, device boot/session ID,
monotonic sequence, occurred/received timestamps, schema version, model version, and
quality flags.

End-to-end semantics are at-least-once plus idempotent effects, not an unsupported claim
of exactly-once across MQTT, Kafka, and InfluxDB. Collect commits offsets only after a
successful idempotent write. De-duplication uses event ID/sequence and a bounded state
store. The current collector marks a gap and advances immediately; a bounded waiting
watermark/recalculation workflow is a separate operational capability and is not implied here.

Consumers reject a raw record whose Kafka key is not exactly
`{tenantId}:{deviceId}:{meterId}` and reject a catalog record whose key is not its meter ID;
otherwise a misconfigured producer could silently move one ordering domain across partitions.
JSON/contract failures and key violations go directly to an environment-qualified DLT. Transient
processing, Redis, InfluxDB, and Kafka publication failures use blocking exponential retry while the
partition is held, then publish the original key/value plus Kafka exception/origin headers to a DLT.
The recovered offset is committed only after the DLT producer (idempotence enabled, `acks=all`) confirms
the durable write. DLT topics must be provisioned with at least the source partition count, replication
factor 3, and `min.insync.replicas=2`; automatic topic creation is disabled. Operational replay copies
the unchanged key/value back to the source topic after the cause is corrected.

## Controlled DLT replay

DLT replay is a platform operation, not a tenant API and not an automatic retry loop.
Only a platform administrator with `platform:all` may submit an exact retained
`dltTopic/partition/offset` to:

```http
POST /api/v1/operations/kafka-dlt/replays
Content-Type: application/json

{
  "dltTopic": "octopus.production.telemetry.raw.dlt.v1",
  "partition": 7,
  "offset": 48192,
  "reason": "Parser version 3.4.1 is deployed and incident INC-2041 approved replay"
}
```

The server maps the DLT to an allowlisted source topic; callers cannot choose an arbitrary
destination. It reads the exact retained record and reserves that source position in PostgreSQL
before publishing. The Kafka key, nullable value bytes, partition, and business headers are
preserved. Old `kafka_dlt-*` diagnostic headers are removed, and the server adds replay job,
root, and attempt headers. A confirmed destination partition/offset/timestamp or a bounded failure
summary is stored in `kafka_dlt_replay_job` and `security_audit_event`, together with the actor,
session, reason, source position, payload size, and a key digest. Payload bytes are never copied
into PostgreSQL.

The unique source-position constraint means the same DLT record cannot be submitted twice.
If a replayed record fails and returns to a new DLT offset, its root and attempt headers follow it;
the default chain limit is three (`OCTOPUS_DLT_REPLAY_MAXIMUM_ATTEMPTS`). Raising this limit must
be an explicit incident decision. Replay does not remove or commit the DLT record.

PostgreSQL and Kafka do not share a transaction. If Kafka confirms the publish but the final
ledger update fails, the job deliberately remains `PENDING` and a second job cannot be created for
that source position. Operators must inspect the destination topic/partition and application
effects before changing state manually; blind resubmission could duplicate delivery. The telemetry
pipeline remains at-least-once and its effects must remain idempotent.

Retrieve the durable result with `GET /api/v1/operations/kafka-dlt/replays/{jobId}`. A not-found
response can also mean the requested source offset has aged out of Kafka retention. Operational
procedure must therefore correct the root cause and verify source/DLT retention before approval.

If multiple ingress producers cause a lower sequence to arrive after a newer one, collect
does not discard it as a duplicate. It writes the canonical raw value with
`OUT_OF_ORDER` quality, marks only that event as processed, and keeps the newer cumulative
state pointer. Delta remains zero for that late point; retained Kafka raw data is the
authoritative source for a later ordered replay/recalculation.

The collector uses manual-immediate Kafka acknowledgement. It acknowledges only after a
synchronous InfluxDB write, normalized Kafka publish, and meter-state update, or after a deterministic invalid event
has been durably published to quarantine. Failed records remain unacknowledged during retry and are
acknowledged only after confirmed DLT publication. Redis state is tenant-namespaced and reconstructible
from retained raw Kafka events.

Ordering state includes `bootId`, source event ID, sequence, event time, and cumulative value. A device
restart therefore opens a new sequence domain instead of leaving every post-restart sequence permanently
`OUT_OF_ORDER`. When a new boot becomes current, the previous boot is retained in a bounded retired-boot
set; a delayed packet from that retired boot is written with `OUT_OF_ORDER` quality but cannot roll the
cumulative pointer backward. Redis uses one cluster-slot-safe Lua operation to retire the old boot,
replace the current pointer, and write the event de-duplication key atomically. A repeated source event
heals a missing de-duplication key, while a different event reusing the current boot/sequence is durably
quarantined as a sequence collision rather than silently acknowledged. Normalized telemetry schema v2
carries `bootId` as a field (not a high-cardinality Influx tag); v1 records remain readable for replay.

The normalized event keeps the raw event's ID. Alarm consumers de-duplicate per rule and
source event in PostgreSQL, so a collector retry may safely republish after an Influx write
without opening or incrementing an incident twice. See [Alarm evaluation](alarm-pipeline.md).

Meter and model configuration is projected into the collector rather than queried from
PostgreSQL for every sample. Management writes the meter and a versioned
`MeterConfigurationChanged` event to a transactional outbox in the same database
transaction. A `SKIP LOCKED` relay publishes the event to Kafka with the meter ID as the
partition key. Collectors acknowledge the configuration event only after atomically
replacing the Redis projection when its configuration version is not older. Delivery is
at-least-once; deterministic versioned projection makes duplicate events harmless.
Topic names are environment-qualified, for example
`octopus.production.catalog.meter-configuration.v1`, so clusters cannot accidentally
consume another environment's catalog projections.

## Meter calculation

Raw cumulative readings are immutable. Derived `delta`, interval accumulation, and net
values include algorithm version and source event IDs so they can be replayed. Reset,
rollover, replacement, clock regression, and estimated data are explicit quality states.
Partition ownership keeps a meter's state on one active consumer. State is checkpointed
and reconstructible from raw Kafka retention; Redis is acceleration, never the sole copy.

InfluxDB tags contain bounded-cardinality identifiers used for filtering (tenant bucket
strategy, device, meter, parameter, quality). Values, sequence, and model version are
fields. Names and arbitrary user labels are not tags. Retention/downsampling policies are
per product tier and legal region.
