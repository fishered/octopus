# Kubernetes deployment

Render the base with `kubectl kustomize deploy/k8s/base`. The repository intentionally
does not contain a Secret value file. Create `octopus-secrets` through External Secrets,
Sealed Secrets, or your cloud secret manager before applying workloads.

Required keys include:

- `OCTOPUS_PG_URL`, `OCTOPUS_PG_USERNAME`, `OCTOPUS_PG_PASSWORD`
- `OCTOPUS_CONTROL_PG_URL`, `OCTOPUS_CONTROL_PG_USERNAME`, `OCTOPUS_CONTROL_PG_PASSWORD`
- `OCTOPUS_REDIS_PASSWORD`
- `OCTOPUS_INFLUX_TOKEN`
- `OCTOPUS_JWT_ISSUER`, `OCTOPUS_JWT_KEY_ID`
- `OCTOPUS_JWT_PUBLIC_KEY`, `OCTOPUS_JWT_PRIVATE_KEY` (mounted PEM resource paths)
- `OCTOPUS_REFRESH_TOKEN_HMAC_KEY` (base64, at least 256 bits)
- `OCTOPUS_TOTP_AES_KEY` (base64, exactly 256 bits; replace with KMS adapter in production)
- `OCTOPUS_MQTT_KEY_STORE_PASSWORD`, `OCTOPUS_MQTT_TRUST_STORE_PASSWORD` when MQTT mTLS is enabled
- `OCTOPUS_CA_PG_URL`, `OCTOPUS_CA_PG_USERNAME`, `OCTOPUS_CA_PG_PASSWORD`
- `OCTOPUS_CA_BOOTSTRAP_HMAC_KEY` (base64, at least 256 bits)
- `OCTOPUS_CA_SIGNING_KEY_STORE_PASSWORD` and `OCTOPUS_CA_SIGNING_KEY_ALIAS` for the local PKCS#12 adapter

The base selects S3 object storage. Each environment overlay must replace
`OCTOPUS_STORAGE_S3_BUCKET` and `OCTOPUS_STORAGE_S3_REGION` with provisioned values. The
application uses the AWS SDK default credentials chain; prefer a service-account workload
identity/IRSA binding with least-privilege access to the configured tenant-prefixed bucket
path. Do not put access keys in `octopus-config`. If `OCTOPUS_STORAGE_S3_ENCRYPTION=KMS`, set
`OCTOPUS_STORAGE_S3_KMS_KEY_ID` and grant the pod identity the required KMS permissions.
MinIO/S3-compatible overlays may also set `OCTOPUS_STORAGE_S3_ENDPOINT` and
`OCTOPUS_STORAGE_S3_PATH_STYLE=true`.

Local mode is available with `OCTOPUS_STORAGE_TYPE=local`, but a pod filesystem or
`emptyDir` is not durable and is not shared between replicas. Mount an intentionally
provisioned volume at `OCTOPUS_STORAGE_LOCAL_ROOT`, or use S3 for replicated deployments.
Changing provider configuration does not migrate existing objects; follow the migration
boundary documented in [Object storage](../../docs/object-storage.md).

`octopus-control` supports a PKCS#12 client key store and trust store mounted from the
optional `octopus-mqtt-tls` Secret as `client.p12` and `truststore.p12`. The base keeps
`OCTOPUS_MQTT_ENABLED=false`; an environment overlay should set the broker address, enable
MQTT, set `OCTOPUS_COMMAND_DISPATCH_ENABLED=true`, and provide that Secret. Each pod uses
its hostname as the persistent MQTT client ID.
`octopus-control` is therefore deployed as a StatefulSet with a headless governing Service;
the stable ordinal keeps the broker session/client ID recoverable after pod recreation.
During scale-down it unsubscribes first, drains Kafka handoffs, and only then disconnects.

The base keeps `OCTOPUS_CA_SIGNING_MODE=disabled`. For private development environments,
mount `issuing-ca.p12` from the optional `octopus-ca-signing` Secret and set the mode to
`local-pkcs12`. Production must replace that adapter with HSM/KMS signing; root CA private
keys must never be placed in a Kubernetes Secret.

Production Kafka, PostgreSQL, Redis, InfluxDB, and MQTT should be managed operators or
external services. `compose.yaml` is development-only and its single Kafka broker does
not represent the production replication policy.

Provision the environment-qualified raw, quarantine, normalized, meter-configuration,
command-requested, command-status, shadow-reported, and shadow-quarantine topics before rolling out services. Command topics
must use the same partition count and `{tenantId}:{deviceId}` partition key. Raw and normalized topics must use the
same ordering-domain partition strategy; production topics require replication factor at
least three and `min.insync.replicas` at least two.

`octopus-control` owns a separate PostgreSQL database (`octopus_control`). Do not point its
JDBC URL at the management database. The base leaves dispatch disabled so a partially
configured MQTT/AWS IoT provider cannot consume and repeatedly fail command requests.

Presence is a Redis lease, not a durable truth claim. Tune
`OCTOPUS_DEVICE_PRESENCE_LEASE_DURATION` above the expected heartbeat/telemetry interval
plus clock and network jitter. Redis persistence is useful for `lastSeenAt`, but an active
lease still expires automatically when heartbeats stop.

`deploy/k8s/optional/collect-keda.yaml` requires KEDA. Kafka partitions cap useful
collector parallelism; set partition count from measured ordering domains and throughput,
then scale replicas by consumer lag.
