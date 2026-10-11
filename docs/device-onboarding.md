# Device onboarding

For device types and thing models that already exist, the management service exposes a one-call
onboarding path:

```http
POST /api/v1/device-onboardings
Idempotency-Key: provision-site-a-meter-001
```

The caller needs both `device:create` and `device:configure` in the target organization scope.
The request creates the logical `Device`, commissions its physical `DeviceActual`, and writes an
active connector binding in one tenant transaction. A validation or persistence failure rolls
back all three records.

```json
{
  "organizationId": "00000000-0000-0000-0000-000000000001",
  "facilityId": null,
  "deviceTypeId": "00000000-0000-0000-0000-000000000002",
  "thingModelId": "00000000-0000-0000-0000-000000000003",
  "modelVersion": 1,
  "code": "meter-001",
  "displayName": "Main incomer meter",
  "hardwareSerial": "HW-001",
  "manufacturer": "Acme",
  "firmwareVersion": "1.0.0",
  "certificateId": null,
  "pluginId": "mqtt-json",
  "codecId": "json",
  "configRef": "secret://mqtt/site-a"
}
```

`configRef` is an opaque reference, not inline credentials. The binding change is emitted through
the existing transactional outbox and projected into control for fail-closed command routing.
The API intentionally requires a pre-existing type/model: AI-generated drafts and vendor payloads
must be reviewed and validated before this mutation is called.

`Idempotency-Key` is required and may contain at most 128 characters. Its scope is the current
tenant and authenticated account. Retrying the same key with the same payload returns the original
onboarding receipt without creating another device, physical instance, or connector binding.
Reusing the key with a different payload returns HTTP `409` with
`DEVICE_ONBOARDING_IDEMPOTENCY_CONFLICT`.

The claim, all three asset records, the connector-binding outbox event, and the completed receipt
commit in one tenant transaction. Concurrent retries therefore wait for the winning transaction
and replay its result; a failed transaction releases the key through rollback.
