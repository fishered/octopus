# Analytics and dashboards

## Storage ownership and query boundary

PostgreSQL owns meter metadata, canonical units, organization paths, dashboard definitions,
widget configuration, ownership, status, and optimistic versions. InfluxDB owns the
high-volume `meter_reading` time series. A dashboard never copies telemetry into PostgreSQL.

Clients cannot submit Flux. The management API accepts a provider-neutral, allowlisted
query model and the Influx adapter is the only component that renders Flux. Every generated
query filters all of the following values supplied by trusted server-side context:

```text
measurement = meter_reading
tenant_id   = current tenant
meter_id    = authorized meter
_field      = raw_value | delta | interval_accumulation
```

Supported aggregations are `MEAN`, `SUM`, `MIN`, `MAX`, and `LAST`. Raw readings cannot use
`SUM`. `DELTA` is valid only for cumulative meters. `INTERVAL_ACCUMULATION` is valid only for
cumulative or interval-delta meters. These rules are shared by ad-hoc analytics queries and
persisted dashboard widgets, so a saved widget cannot bypass the interactive query policy.

## Bounded queries

The analytics boundary enforces:

- a maximum range of 366 days;
- at most 10,000 result buckets;
- whole-second buckets from 1 second through 31 days;
- an end instant strictly after the start instant.

Time-series bounds and bucket starts are UTC `Instant` values. The API additionally returns
a local bucket label in a validated IANA region zone. If the caller does not select a zone,
the effective zone is resolved as:

```text
facility.zone_id -> organization.zone_id -> tenant.default_zone_id
```

Offsets such as `+08:00` are not accepted as persisted display zones because they do not
carry daylight-saving or historical transition rules.

Example:

```text
GET /api/v1/analytics/meters/{meterId}/series
  ?start=2026-08-01T00:00:00Z
  &end=2026-08-02T00:00:00Z
  &bucketSeconds=900
  &field=INTERVAL_ACCUMULATION
  &aggregation=SUM
  &zoneId=Asia/Shanghai
```

The service requires `analytics:view` against the concrete Meter. Tenant scope,
organization-subtree scope, and explicit Meter resource grants are evaluated before any
Influx query is made.

## Dashboard aggregate

`Dashboard` is a tenant-owned aggregate rooted in one Organization. It records the owner
account, stable code, display metadata, default IANA zone, `ACTIVE/ARCHIVED` status,
optimistic version, and an ordered list of widgets.

A widget stores only:

- a Meter ID and title;
- `LINE`, `BAR`, `SINGLE_VALUE`, or `GAUGE` visualization;
- an allowlisted value field and aggregation;
- a bounded bucket size;
- a validated 24-column-grid layout (`x`, `y`, `width`, `height`);
- a deterministic sort order.

Widgets cannot contain Flux or executable calculation expressions. A Meter used by a
widget must be active and located inside the dashboard Organization subtree.

## Authorization, tenancy, and concurrency

Creating a dashboard requires `dashboard:configure` on its Organization. Reading requires
`dashboard:view` on the Dashboard. Adding a widget requires both `dashboard:configure` on
the Dashboard and `analytics:view` on the referenced Meter. Normal operators may be
authorized through an Organization subtree or explicit resource grants; Organization
administrators remain confined to their subtree. A platform administrator must still enter
an explicit tenant scope for tenant APIs.

Both `dashboard` and `dashboard_widget` use PostgreSQL `ENABLE/FORCE ROW LEVEL SECURITY`
policies bound to `app.tenant_id`. Their tenant-scoped unique constraints and indexes begin
with `tenant_id`. Business foreign keys are intentionally omitted; the application service
validates the Organization and Meter relationship in the tenant transaction.

Widget mutation uses the Dashboard aggregate version:

```text
POST /api/v1/dashboards/{dashboardId}/widgets
If-Match: "0"
```

A successful response returns the new version as an ETag. A stale `If-Match` returns
`409 DASHBOARD_VERSION_CONFLICT`, preventing concurrent editors from silently overwriting
each other. The same aggregate version protects metadata edits, archiving, and every Widget
add/update/remove operation; there is no second, competing Widget version stream.

## API examples

Create a dashboard:

```json
POST /api/v1/dashboards

{
  "organizationId": "7dcd0b61-24d4-46f9-9aec-54cd217c2859",
  "code": "site_energy",
  "displayName": "Site energy",
  "description": "Daily operating view",
  "defaultZoneId": "Europe/London"
}
```

Add a widget:

```json
POST /api/v1/dashboards/{dashboardId}/widgets
If-Match: "0"

{
  "meterId": "f77f930e-4b08-4763-b6b0-62ef32c0f52c",
  "title": "Quarter-hour consumption",
  "visualization": "BAR",
  "field": "INTERVAL_ACCUMULATION",
  "aggregation": "SUM",
  "bucketSeconds": 900,
  "layout": { "x": 0, "y": 0, "width": 12, "height": 4 },
  "sortOrder": 10
}
```

Read a dashboard and its ordered widgets:

```text
GET /api/v1/dashboards/{dashboardId}
```

List dashboards in an Organization subtree (active by default):

```text
GET /api/v1/dashboards?organizationId={organizationId}&includeArchived=false&limit=100
```

Manage the remaining lifecycle with the latest Dashboard ETag:

```text
PUT    /api/v1/dashboards/{dashboardId}
POST   /api/v1/dashboards/{dashboardId}/archive
PUT    /api/v1/dashboards/{dashboardId}/widgets/{widgetId}
DELETE /api/v1/dashboards/{dashboardId}/widgets/{widgetId}
```

Archived dashboards remain readable for audit and historical operations, but their metadata
and Widgets cannot be changed. Listing excludes them unless `includeArchived=true`.
