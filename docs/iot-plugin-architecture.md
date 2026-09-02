# Pluggable IoT components

Octopus keeps device semantics in `octopus-api` and the control/collect bounded contexts.
Transport implementations are plugins behind `octopus-iot-spi`; a plugin must not access
business databases directly. This lets MQTT, AWS IoT, HTTP/CoAP, and edge protocols share
the same command, telemetry, presence, and Shadow lifecycle.

## Extension points

`DeviceTransportPlugin` owns a provider connection and exposes `publish`, `start`, `health`,
and `stop`. `ProtocolCodec` is intentionally separate from transport so JSON, Protobuf, and
vendor payloads can be reused over MQTT, HTTP, or a cloud bridge. `DeviceBinding` is a
versioned projection from management and contains only routing identifiers; plugins must not
query PostgreSQL for every message.

The registry is assembled at startup and rejects duplicate plugin IDs. Plugin IDs and codec
IDs are lowercase stable identifiers. The descriptor declares the supported semantic message
kinds and an opaque configuration schema for management validation.

## Current plugin

`octopus-control` contains the first implementation, `mqtt-json`. It wraps the existing Paho
MQTT client and publishes the provider-neutral command envelope with QoS 1. The existing
MQTT ingress lifecycle remains responsible for telemetry, command results, and reported Shadow
subscriptions; the plugin boundary is therefore incremental and does not weaken the durable
Kafka handoff rules.

When MQTT is enabled, command dispatch is routed through the plugin registry using
`octopus.iot.default-plugin` (default `mqtt-json`). When no plugin is installed, control keeps
the existing unavailable adapter and fails commands explicitly instead of silently dropping
them.

## Adding a plugin

1. Implement `DeviceTransportPlugin` in a separate module or service.
2. Preserve the original correlation ID and return `ACCEPTED` only after the provider accepts
   the publish. Device-side de-duplication remains mandatory because delivery is at least once.
3. Normalize inbound messages into the API contracts before Kafka publication. Validate topic,
   payload identity, size, QoS, retained state, and tenant/device binding at the edge.
4. Publish a versioned binding projection from management and route by binding version. Do not
   introduce provider names or policy ARNs into the `Device` aggregate.
5. Expose health and metrics for connection state, publish latency, retries, rejected messages,
   and quarantine/DLT outcomes.

Plugins with native SDKs or serial protocols (Modbus, OPC-UA, BACnet) should run in an edge
gateway or sidecar. The in-process SPI is intended for trusted, provider-neutral adapters.
