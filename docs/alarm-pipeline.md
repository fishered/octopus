# Alarm evaluation and lifecycle

## Data flow and delivery semantics

After a collector has normalized a reading and written the deterministic InfluxDB point, it publishes
`NormalizedTelemetry` to the environment-qualified normalized topic. The collector advances its Redis
meter state only after Kafka acknowledges that publish. A failure therefore causes the raw event to be
retried; the Influx write is an idempotent overwrite and the normalized event retains the same
`sourceEventId`.

`octopus-mgmt` consumes the normalized stream with manual acknowledgement. The event's verified contract
tenant establishes `TenantContext`, and PostgreSQL RLS is set on the transaction before alarm data is read
or changed. A Kafka offset is acknowledged only after the evaluation transaction commits.

Delivery is at least once. `alarm_evaluation_event` has a unique key of
`(tenant_id, rule_id, source_event_id)`, so a replay cannot increment or reopen an incident twice. This
ledger must be retained for at least the longer of the Kafka replay window and the product's alarm audit
window.

## Rule semantics

A rule targets one meter and its parameter and selects one normalized value:

- `RAW`
- `DELTA`
- `INTERVAL_ACCUMULATION`

Comparisons support greater/less than, inclusive comparisons, equality, and inequality. Greater/less
rules may use a separate clear threshold to provide hysteresis and prevent chattering. Equality rules use
the trigger threshold for clearing.

Only one `OPEN` or `ACKNOWLEDGED` incident may exist for a rule/meter pair. Continued matching readings
update the latest value and occurrence count. A non-matching reading clears the incident only when it
crosses the configured clear threshold. `INVALID` and `OUT_OF_ORDER` readings are recorded in the
idempotency ledger but do not mutate current alarm state; late data cannot roll an incident backward.

## Access control

Rule creation, rule/incident reads, device-scoped incident lists, and acknowledgement use the existing
JWT session and deny-by-default authorization policy. Tenant RLS is always applied. Organization
administrators are limited to their organization subtree, while operators require `alarm:*` permissions
plus either an organization scope or a device resource grant.

The HTTP resources are:

- `POST /api/v1/alarm-rules`
- `GET /api/v1/alarm-rules/{ruleId}`
- `GET /api/v1/alarm-incidents/{incidentId}`
- `GET /api/v1/alarm-incidents?deviceId=...&state=OPEN&limit=50`
- `POST /api/v1/alarm-incidents/{incidentId}/acknowledge`

Notification routing, escalation schedules, and outbound webhook/email/SMS delivery are separate future
adapters. They should consume transactional alarm lifecycle events rather than being invoked inside the
evaluation transaction.
