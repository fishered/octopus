# Domain model

## Identity and access

- `Tenant`: SaaS boundary, status, home region, default zone, locale, quotas.
- `Organization`: tenant-owned hierarchy using `parent_id` plus a materialized path.
- `Account`: global login identity; active memberships attach it to tenants/organizations. Membership
  suspension and role-assignment revocation are audited authorization changes that immediately
  invalidate the account's tenant JWT generation.
- `Role`, `Permission`, `Menu`: role grants permissions; menus reference permissions.
- `DataScope` and `ResourceGrant`: organization subtree and facility/device action scope.
- `Session`, `MfaFactor`, `SecurityEvent`: authentication lifecycle and audit evidence.

`Permission` is a global action vocabulary with an explicit tenant-delegation boundary. The
management API exposes only tenant-assignable entries for role configuration. `Menu` is global,
read-only presentation metadata: the current-user endpoint returns active entries whose required
permission is present in the authenticated principal. Menu visibility never grants API access.

`ManagedResourceGrant` is the lifecycle record behind explicit operator data access. It is scoped
to a tenant membership and a concrete device, facility, or meter action. Active grants are loaded
into the authorization snapshot; revoked grants remain as audit history and are excluded from new
JWTs. Grant creation and revocation share the same authorization-generation invalidation mechanism
as role changes.

## Asset and telemetry catalog

- `Facility`: tenant-owned hierarchy or spatial area; PostGIS geometry uses SRID 4326.
- `DeviceType`: protocol/capability class with a declaration-only JSON capability object and
  `ACTIVE`/`RETIRED` lifecycle. A tenant-scoped type can be created only inside that tenant's
  database scope. Global types (`tenant_id IS NULL`) are read-only platform vocabulary delivered
  through controlled migrations; tenant APIs cannot create or mutate them. A tenant type may not
  shadow a visible global type with the same code, so code lookup remains deterministic.
- `ThingModel`: versioned model definition, immutable after publication; every parameter
  binding declares `READ_ONLY`, `WRITE_ONLY`, or `READ_WRITE` access.
- `Device`: logical managed asset and ownership/placement.
- `DeviceActual`: physical hardware identity, serial, firmware, connectivity and certificate reference.
- `Parameter`: semantic quantity definition and data type.
- `Unit`: UCUM-compatible code, symbol, dimension, scale, and offset.
- `Meter`: a readable/calculated/billable/gateway point attached to a device/facility.

Meter kinds are `STANDARD`, `CALCULATED`, `BILLING`, and `GATEWAY`. Capabilities should
be composed rather than encoded into a growing subtype table. Energy parameter semantics
distinguish `INSTANTANEOUS`, `CUMULATIVE`, `INTERVAL_DELTA`, and `NET`; unit conversion is
dimension-checked and decimal precision is retained.

Each `ThingModel` row is one immutable version. `thing_model_parameter` pins the parameter,
source unit, required flag, and deterministic order used by that version. A meter can only
be created from a parameter/unit pair declared by the published model version currently
assigned to its device. Calculated meters require a JSON expression; non-calculated meters
cannot carry one. Rollover modulus is valid only for cumulative parameters.

`Facility` accepts GeoJSON Geometry objects at the HTTP/domain boundary and persists them
as PostGIS SRID 4326 geometry. Coordinates are constrained to WGS84 longitude/latitude
ranges. Facility time zones must be IANA region IDs rather than ambiguous numeric offsets.
Spatial viewport queries support windows crossing the antimeridian and still apply tenant,
organization-subtree, permission, and explicit resource-grant checks to every result.

`DeviceActual` is the one-to-one commissioned hardware record for a logical `Device`.
Hardware serial and certificate references are tenant-unique. The certificate ID is an
external reference owned by `octopus-ca`; there is deliberately no cross-service database
foreign key. Connectivity timestamps are monotonic so delayed events cannot move
`last_seen_at` backwards.

## Device Shadow

- `DeviceShadow`: latest device-reported state, device-owned monotonic Shadow version,
  optional applied Desired version, and UTC reported/received timestamps.
- `DesiredShadowRequest`: audited, idempotent, optimistically versioned desired-state intent
  linked one-to-one to a reliable device command.
- `device_desired_shadow_current`: latest Desired pointer and state per tenant/device; older
  requests remain as lifecycle history rather than being overwritten.

Desired state accepts only writable parameters from the device's active published thing
model. Existing bindings migrate as `READ_ONLY`. A request moves through command delivery
states and becomes `APPLIED` only after a reported Shadow names the same Desired version and
contains matching values. Desired JSON is not returned by the management API because it can
contain `WRITE_ONLY` control values.

## Alarms

- `AlarmRule`: tenant-owned threshold rule bound to a meter/parameter and its owning device and organization.
- `AlarmIncident`: one active lifecycle per rule/meter, moving through `OPEN`, `ACKNOWLEDGED`, and `CLEARED`.
- `AlarmEvaluationEvent`: per-rule/source-event idempotency ledger for Kafka replay.

Rules evaluate normalized raw, delta, or interval-accumulation values. Separate trigger and clear
thresholds provide hysteresis for greater/less comparisons. Incidents retain the triggering value,
latest value, occurrence count, event timestamps, acknowledgement actor/time, and optimistic version.
Invalid or out-of-order telemetry never changes current incident state.

## Analytics and dashboards

- `TelemetryQuery`: provider-neutral, tenant/Meter-scoped request containing only an
  allowlisted value field, aggregation, UTC range, and bounded bucket duration.
- `Dashboard`: Organization-rooted aggregate with owner, IANA display zone, lifecycle
  status, optimistic version, and ordered widgets.
- `Widget`: Meter-bound visualization configuration with allowlisted telemetry semantics,
  bounded bucket size, and a validated 24-column-grid layout.

PostgreSQL stores dashboard configuration but not time-series results. InfluxDB queries are
rendered behind the analytics port; neither clients nor widgets can persist arbitrary Flux.
Widget creation additionally verifies `analytics:view` on the concrete Meter and requires
the Meter Organization path to remain inside the Dashboard Organization subtree. See
[Analytics and dashboards](analytics-and-dashboards.md). Metadata edits, archive, and Widget
add/update/remove operations all advance the same Dashboard version. Archived dashboards
remain readable but immutable.

PostgreSQL owns configuration, identities, relationships, current state pointers, and
audit metadata. InfluxDB owns high-volume time series. Kafka raw retention is the replay
source. Large binary artifacts belong to object storage, not PostgreSQL.

No business foreign keys are required at the database layer, matching AcuCloud's operational
guideline; application transactions enforce aggregate invariants. Every relationship has
an index, every tenant unique constraint begins with `tenant_id`, and deletion is explicit
status/tombstone plus audit rather than silent cascade.
