# Device presence and governed shadow

## Presence semantics

Presence is an operational estimate, not a durable device status. `octopus-control`
refreshes a Redis lease only after a live authenticated uplink has completed its durable
Kafka/database handoff:

- accepted telemetry after Kafka acknowledgement;
- persisted command result;
- non-retained reported Shadow after Kafka acknowledgement.

Redis stores a monotonic `lastSeenAt` and a short-lived lease. A read returns:

- `NEVER_SEEN` when no observation exists;
- `ONLINE` while the lease for the latest observation is active;
- `OFFLINE` after that lease expires.

Delayed observations cannot move `lastSeenAt` backwards. Retained MQTT messages can rebuild
Shadow but cannot make an offline device appear online. Presence failure is logged but does
not fail the already durable telemetry/shadow/command-result path.

## Reported Shadow flow

```text
octopus/{tenantId}/devices/{deviceId}/shadow/reported
  -> topic/payload identity + QoS + JSON object + 256 KiB validation
  -> Kafka shadow.reported.v1 keyed by tenantId:deviceId
  -> octopus-mgmt tenant transaction
  -> inbox de-duplication + FORCE RLS PostgreSQL projection
  -> GET /api/v1/devices/{deviceId}/monitoring/shadow
```

Payload contract:

```json
{
  "schemaVersion": 1,
  "reportId": "UUID",
  "tenantId": "UUID",
  "deviceId": "UUID",
  "shadowVersion": 12,
  "state": { "relay": true, "firmware": "1.2.3" },
  "appliedDesiredVersion": 4,
  "reportedAt": "2026-08-15T00:00:00Z"
}
```

`shadowVersion` is owned by the device/provider and must increase when reported state
changes. Older versions are ignored. A different payload for the same version is rejected
as a protocol conflict. Event IDs make Kafka replay idempotent; projection versions provide
optimistic concurrency control. `appliedDesiredVersion` is optional for backward
compatibility. When present, it identifies the platform Desired version applied by the
device; it does not replace the device-owned monotonic `shadowVersion`.

The read endpoints require `device:view` plus tenant organization/resource scope:

```text
GET /api/v1/devices/{deviceId}/monitoring/presence
GET /api/v1/devices/{deviceId}/monitoring/shadow
GET /api/v1/devices/{deviceId}/monitoring/shadow/desired
```

## Desired Shadow governance

Desired state is a versioned, audited control intent rather than an unrestricted JSON
document. A published thing-model binding declares each parameter as:

- `READ_ONLY`: telemetry/reported state only;
- `WRITE_ONLY`: accepts Desired intent but should not be exposed as reported state;
- `READ_WRITE`: accepts Desired intent and may also appear in reported state.

Bindings created before this capability default to `READ_ONLY`, so migration cannot silently
make existing telemetry fields controllable. A Desired update rejects unknown, read-only,
null, or incorrectly typed properties. Canonical state is limited to 60 KiB and the resulting
command remains under the command pipeline's 64 KiB limit. `WRITE_ONLY` values must be
treated as control values, not a place for raw long-lived secrets; use secret references.

The write endpoint requires `device:operate`, organization/resource authorization,
`Idempotency-Key`, and an `If-Match` Desired version:

```text
PUT /api/v1/devices/{deviceId}/monitoring/shadow/desired
If-Match: "0"
Idempotency-Key: client-generated-stable-key

{
  "state": { "relay": true, "limit": 10 },
  "ttlSeconds": 120
}
```

Use version `0` for the first request. GET and PUT responses return the current Desired
version as an ETag. A stale `If-Match` fails instead of overwriting a concurrent update.
Repeating the same idempotency key and request returns the original result; reusing it for a
different device, expected version, TTL, or state is a conflict. The response intentionally
returns lifecycle metadata without echoing Desired JSON, avoiding disclosure of
`WRITE_ONLY` values.

The management transaction atomically stores the Desired request, its command projection,
and the Kafka outbox event. The reliable command uses operation `shadow.desired.patch` and
this payload:

```json
{
  "schemaVersion": 1,
  "desiredRequestId": "UUID",
  "expectedDesiredVersion": 3,
  "desiredVersion": 4,
  "modelVersion": 8,
  "state": { "relay": true, "limit": 10 }
}
```

The lifecycle is:

```text
REQUESTED -> DISPATCHED -> ACKNOWLEDGED -> EXECUTED -> APPLIED
    |             |              |
    +-------------+--------------+------> FAILED / EXPIRED
    +-----------------------------------> SUPERSEDED
```

Provider or device command success advances the request to `EXECUTED`, but only a reported
Shadow can prove convergence. The request becomes `APPLIED` when reported
`appliedDesiredVersion` equals the current Desired version and the reported state contains
every Desired property with equal JSON values. Extra reported properties are allowed. A
newer Desired request marks the prior non-terminal request `SUPERSEDED`.

All Desired tables are tenant-scoped by both the MyBatis tenant interceptor and PostgreSQL
`FORCE ROW LEVEL SECURITY`; organization and explicit device grants are checked before any
read or write. Times are UTC `Instant`/`timestamptz`.
