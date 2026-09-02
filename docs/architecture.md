# Architecture

## Bounded contexts

| Service | Owns | Does not own |
| --- | --- | --- |
| `octopus-ca` | manufacturing enrollment, device identity, certificate issue/rotate/revoke, CRL/OCSP state | interactive user login |
| `octopus-control` | MQTT connectivity, device shadow, durable command execution lifecycle, protocol/AWS IoT adapters | user authorization, telemetry history and analytics |
| `octopus-collect` | Kafka ingestion, validation, de-duplication, ordering, meter calculations, Influx writes | asset catalog source of truth |
| `octopus-mgmt` | IAM, tenant/org, facilities, device registry, models, parameters, units, meters, alarms, dashboards | direct MQTT sessions |
| `octopus-agent` | future agent tools, policy, runs, and audit | direct unrestricted database access |

`octopus-mgmt` publishes compact reference-data snapshots/changes. Collectors do not
query PostgreSQL per measurement; they maintain a versioned local/Redis cache and reject
or quarantine readings whose model version is unknown.

User-facing command authorization remains in `octopus-mgmt`; execution is handed to
`octopus-control` through an outbox and Kafka. Both services keep their own PostgreSQL
projection and never share business tables. See [Device command lifecycle](device-command-lifecycle.md).

Online/offline monitoring is a Redis lease derived from accepted uplinks. Reported device
Shadow travels through Kafka into an RLS-protected PostgreSQL projection; retained MQTT
reports rebuild state but deliberately do not renew presence. See
[Device presence and shadow](device-presence-and-shadow.md).

## Deployment

Services are stateless Kubernetes Deployments except for external PostgreSQL, Kafka,
Redis, InfluxDB, MQTT broker, and object storage. Kubernetes DNS is service discovery.
ConfigMaps hold non-secret configuration; Secrets or an external secret operator hold
credentials. Readiness, liveness, startup probes, PodDisruptionBudgets, topology spread,
graceful termination, and HPA based on CPU plus Kafka lag are required production policy.

Object storage is accessed through a provider-neutral port. Local storage is a development
default; the Kubernetes production base selects the separately packaged S3 adapter. Both
adapters physically prefix objects with the immutable tenant UUID. S3 credentials come from
the SDK default credentials chain so workload identity can be used without application-held
keys. See [Object storage](object-storage.md).

Spring Cloud is not a second service registry. Kubernetes owns discovery, configuration,
and workload lifecycle. Individual Spring Cloud components may be introduced for a proven
need (for example Kubernetes configuration reload or gateway behavior), pinned through a
compatible BOM at that time.

HTTP calls use short timeouts, bounded retries with jitter only for idempotent operations,
and circuit breaking at the client/mesh layer. Kafka is the integration backbone. OpenTelemetry
trace context, tenant-safe structured logs, metrics, and audit events are mandatory.

## Global operation

The initial topology is a single write region with regional edge/control collectors.
Every durable record has a globally unique identifier and UTC timestamps. Kafka topics
are region-qualified. A later active-active topology requires an explicit ownership rule
per tenant/device; it must not be simulated with cross-region last-write-wins for commands,
certificates, or cumulative meter state.
