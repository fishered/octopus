# Octopus

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

Octopus is a global, multi-tenant IoT platform for device identity, telemetry collection,
remote control, energy metering, operations, analytics, and future agent capabilities.

The current supported baseline is **v1.0.0**. See [the changelog](CHANGELOG.md) for release
scope and known operational boundaries.

## Modules

```text
octopus
+-- octopus-api          # Versioned cross-service commands, events, and DTO contracts
+-- octopus-commons      # Independently publishable Nexus artifacts
+-- octopus-ca           # Device manufacturing identity and certificate lifecycle
+-- octopus-collect      # Ordered, replayable telemetry processing and calculations
+-- octopus-control      # MQTT device connectivity, command, shadow, and AWS IoT adapters
+-- octopus-iot-spi      # Provider-neutral transport and protocol plugin contracts
+-- octopus-mgmt         # IAM, organizations, assets, alarms, analytics, and dashboards
`-- octopus-agent        # Reserved agent capability boundary
```

Each deployable service uses the package dependency direction below:

```text
interfaces -> application -> domain
infrastructure -> application/domain
```

The domain layer is plain Java. Persistence entities, MyBatis mappers, Kafka clients,
InfluxDB clients, and HTTP controllers remain in infrastructure or interfaces and are
never cross-service contracts.

## Technology baseline

- Java 21 and Maven 3.9+
- Spring Boot 3.5; Spring Cloud components are added only for concrete needs
- Kubernetes-native DNS, ConfigMap/Secret, probes, HPA, and graceful shutdown
- PostgreSQL with PostGIS and Flyway; MyBatis-Plus for ORM
- InfluxDB 2.x for telemetry; Redis/Redisson for cache and coordination
- Kafka for durable event streams; MQTT at the device edge
- Provider-neutral object storage with isolated Local and AWS SDK v2 S3 adapters
- UTC `Instant`/PostgreSQL `timestamptz` on the wire and at rest; IANA time-zone IDs for display and calendar rules

## Build

```bash
./mvnw verify
```

The Maven wrapper is the supported build entry point.

## Local infrastructure

Set non-default development secrets, then start the dependencies:

```powershell
$env:OCTOPUS_PG_PASSWORD = '<local-only-value>'
$env:OCTOPUS_REDIS_PASSWORD = '<local-only-value>'
$env:OCTOPUS_INFLUX_PASSWORD = '<local-only-value>'
$env:OCTOPUS_INFLUX_TOKEN = '<local-only-value>'
docker compose up -d
```

The stack provides PostGIS, Redis, Kafka in KRaft mode, InfluxDB 2.x, and a local MQTT
broker. It is intentionally single-node and is not a production topology.

Render the Kubernetes base with `kubectl kustomize deploy/k8s/base`. See
[Kubernetes deployment](deploy/k8s/README.md) for required Secret keys and optional KEDA scaling.

## Design documents

- [Architecture](docs/architecture.md)
- [Domain model](docs/domain-model.md)
- [Security and tenant isolation](docs/security-and-tenancy.md)
- [Telemetry reliability](docs/telemetry-pipeline.md)
- [Alarm evaluation and lifecycle](docs/alarm-pipeline.md)
- [Analytics and dashboards](docs/analytics-and-dashboards.md)
- [Object storage](docs/object-storage.md)
- [Device command lifecycle and reliability](docs/device-command-lifecycle.md)
- [Device presence and reported shadow](docs/device-presence-and-shadow.md)
- [Device certificate lifecycle](docs/device-certificate-lifecycle.md)
- [EMQX broker integration](docs/emqx-broker-integration.md)
- [Architecture decisions](docs/adr/README.md)
- [IoT plugin architecture](docs/iot-plugin-architecture.md)

## License

Octopus is licensed under the [Apache License, Version 2.0](LICENSE).
