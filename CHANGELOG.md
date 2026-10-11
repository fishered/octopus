# Changelog

## Unreleased

- Add a deterministic IoT plugin runtime with startup rollback, reverse-order shutdown,
  SPI compatibility checks, codec allow-list selection, and actuator health details.
- Add the provider-neutral `octopus-ai-spi` module and the first review-only device
  integration assistant boundary in `octopus-agent`.
- Add an opt-in OpenAI-compatible HTTP provider with model allow-listing, structured output,
  bounded timeouts, sanitized failures, and local proposal validation.
- Remove the hard-coded inbound JSON codec path so installed transport plugins can declare
  and select JSON, Protobuf, or vendor codecs without changing control-domain code.
- Add an atomic one-call device onboarding use case for logical device, physical hardware,
  and active connector binding creation.
- Make one-call device onboarding durably idempotent and concurrency-safe through the required
  `Idempotency-Key` header.

## 1.0.0 - 2026-09-25

Octopus 1.0 establishes the first supported platform baseline for multi-tenant device
identity, telemetry, commands, shadows, alarms, analytics, and provider-neutral IoT
transport plugins.

### Release highlights

- Explicit certificate-to-tenant-to-logical-device binding with an upgrade migration.
- Fail-closed EMQX HTTP authorization scoped to each device topic namespace.
- CSR-bound certificate issuance idempotency; reused keys with different CSRs are rejected.
- Ordered MQTT ingress, command-result and shadow handling, plus graceful in-flight drain.
- Kubernetes health probes, non-root/read-only containers, JWT key mounts, availability
  controls, and optional KEDA collector scaling.
- Maven reactor version aligned to `1.0.0` and CI verification for tests and Kubernetes render.

### Operational boundaries

- `octopus-agent` remains a reserved capability boundary and is not production-functional.
- Production CA signing must use an HSM/KMS implementation; the PKCS#12 adapter is for
  private development only.
- CRL/OCSP publication, automated certificate renewal, and full broker-to-database
  end-to-end tests are planned for 1.1.
- The included Compose stack is development-only and does not represent production HA.
