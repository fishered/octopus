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

The registry is assembled at startup and rejects duplicate plugin IDs, unsupported SPI versions,
and missing codecs. `DeviceTransportPluginRuntime` starts plugins in stable ID order, rolls back
a partial startup, and stops in reverse order. A transport no longer owns a separate Spring
lifecycle, so the same behavior applies to built-in and third-party plugins.

Plugin IDs and codec IDs are lowercase stable identifiers. The descriptor declares the supported
semantic message kinds and an opaque configuration schema for management validation. An inbound
message identifies the plugin that produced it through `InboundMessage.transport()`. The control
dispatcher selects exactly one supporting codec from that plugin's declared codec allow-list;
zero or multiple matches fail closed.

## Current plugin

`octopus-control` contains the first implementation, `mqtt-json`. It wraps the existing Paho
MQTT client and publishes the provider-neutral command envelope with QoS 1. The existing
MQTT ingress lifecycle remains responsible for telemetry, command results, and reported Shadow
subscriptions; the plugin boundary is therefore incremental and does not weaken the durable
Kafka handoff rules.

When MQTT is enabled, command dispatch is routed through the device's active connector binding.
There is deliberately no default-plugin fallback: a missing, disabled, or unavailable binding
fails closed instead of sending a command through an unintended provider. When no plugin is
installed, control keeps the unavailable adapter and fails commands explicitly.

## Adding a plugin

1. Implement `DeviceTransportPlugin` and any `ProtocolCodec` beans in a separate module.
   Package them as Spring Boot auto-configuration if the plugin is delivered as an external JAR:

   ```java
   @AutoConfiguration
   public class VendorPluginAutoConfiguration {
       @Bean DeviceTransportPlugin vendorTransport(VendorClient client) {
           return new VendorTransportPlugin(client);
       }

       @Bean ProtocolCodec vendorCodec(ObjectMapper json) {
           return new VendorProtocolCodec(json);
       }
   }
   ```

   Register the configuration class in
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Once the
   JAR is on the `octopus-control` runtime classpath, Octopus validates and starts it automatically.
2. Preserve the original correlation ID and return `ACCEPTED` only after the provider accepts
   the publish. Device-side de-duplication remains mandatory because delivery is at least once.
3. Normalize inbound messages into the API contracts before Kafka publication. Validate topic,
   payload identity, size, QoS, retained state, and tenant/device binding at the edge.
4. Publish a versioned binding projection from management and route by binding version. Do not
   introduce provider names or policy ARNs into the `Device` aggregate.
5. Expose health and metrics for connection state, publish latency, retries, rejected messages,
   and quarantine/DLT outcomes.

Installed plugin status is available under the `iotPlugins` component of the control service
Actuator health response. The sanitized entry includes plugin/SPI version, supported message kinds,
and codec IDs so operators can verify onboarding choices without inspecting the runtime classpath.
Health details must not include credentials, endpoint tokens, or raw device payloads.

Plugins with native SDKs or serial protocols (Modbus, OPC-UA, BACnet) should run in an edge
gateway or sidecar. The in-process SPI is intended for trusted, provider-neutral adapters.
