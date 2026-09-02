# AWS IoT adapter boundary

`octopus-control` keeps provider SDKs outside the application and domain layers.

- Device commands use `DeviceMessagingPort`. The local implementation publishes MQTT
  QoS 1 messages; an AWS implementation uses the IoT Data Plane publish API without
  changing command application logic. It must return a provider receipt only after AWS
  accepts the publish request; `commandId` remains the device idempotency key.
- Device results use the same `DeviceCommandResult` contract and must be routed to
  `ApplyDeviceCommandResultService`. With MQTT the canonical topic is
  `octopus/{tenantId}/devices/{deviceId}/command-results/{commandId}`. An AWS IoT Rule,
  Basic Ingest route, or Lambda bridge must preserve and validate all three identifiers.
- Device uplinks enter `TelemetryIngressService` as raw payload plus trusted source
  metadata. A broker client, AWS IoT Rule destination, or private-link ingress adapter can
  all call the same service.
- AWS IoT certificate principals and policies must bind a device to only
  `octopus/{tenantId}/devices/{deviceId}/...` topics. Payload tenant/device IDs are still
  compared with the source topic before Kafka publication.
- An AWS IoT Rule forwarding directly to Kafka/MSK must reproduce the same Kafka key
  `{tenantId}:{deviceId}:{meterId}`, quarantine contract, and at-least-once retry behavior.
  If that cannot be guaranteed, forward to the control adapter instead.

The provider boundary deliberately avoids storing AWS Thing names or policy ARNs in the
Device aggregate. Those identifiers belong in an infrastructure mapping/projection so a
tenant can migrate brokers without rewriting its domain identity.

AWS IoT publish acknowledgement is not device execution acknowledgement. A process crash
after provider publish but before the PostgreSQL transaction commits can publish the same
command again. Device firmware must persistently de-duplicate by `commandId` and return its
previous terminal result when possible.

AWS IoT Device Shadow can be adapted at the same boundary, but AWS metadata must first be
normalized to `DeviceShadowReported`. The adapter must preserve tenant/device identity,
monotonic device shadow version, device `reportedAt`, platform `receivedAt`, 256 KiB limit,
and the `{tenantId}:{deviceId}` Kafka key. AWS retained/delta replays must not renew the
Octopus presence lease; only live authenticated activity may do that.

For Desired state, `octopus-mgmt` remains the authorization, model-validation, versioning,
idempotency, and audit authority. The AWS adapter receives the normalized
`shadow.desired.patch` command and translates it to an AWS IoT Shadow update while preserving
`commandId`, `desiredRequestId`, `desiredVersion`, expiry, and tenant/device identity. AWS
Shadow version numbers are provider metadata and must not replace the Octopus Desired
version. The adapter must normalize a confirmed device report back to
`DeviceShadowReported.appliedDesiredVersion`; an AWS publish/update acknowledgement alone
must only advance delivery status, never mark the Desired request `APPLIED`.
