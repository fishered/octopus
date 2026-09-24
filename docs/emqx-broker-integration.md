# EMQX broker integration

Octopus authorizes MQTT access from the operational certificate that EMQX has already
validated against the configured issuing CA. Do not accept a client-provided username,
topic field, or arbitrary certificate serial as proof of identity.

## Required broker policy

1. Require TLS client certificates and validate the complete client chain.
2. Configure the HTTP authorizer to call `POST /internal/emqx/acl` over a private network
   with `X-EMQX-API-Key` set to the same high-entropy value as
   `OCTOPUS_CA_EMQX_WEBHOOK_API_KEY`.
3. Send the broker-derived peer certificate as `peerCertificate` (EMQX placeholder
   `${peercert}`), together with `${action}` and `${topic}`.
4. Set the broker's unmatched authorization result to `deny` and place no permissive
   authorizer after Octopus.

Conceptual request body:

```json
{
  "peerCertificate": "${peercert}",
  "action": "${action}",
  "topic": "${topic}"
}
```

The endpoint always returns HTTP 200 with `{"result":"allow"}` or
`{"result":"deny"}`. Invalid API keys, malformed certificates, database failures,
expired/revoked certificates, and malformed topics all return an explicit deny. This is
intentional: EMQX can treat non-success HTTP responses as `ignore`, which may allow a later
authorization source to decide.

The optional `POST /internal/emqx/auth` endpoint accepts the same peer certificate and is
provided for broker integrations that need an HTTP authentication decision. Standard EMQX
deployments may use mutual TLS for authentication and Octopus HTTP authorization for ACLs.

## Allowed topic namespace

For the tenant and logical device bound to the certificate, the v1 policy permits:

- publish `octopus/{tenantId}/devices/{deviceId}/telemetry`
- publish `octopus/{tenantId}/devices/{deviceId}/shadow/reported`
- publish `octopus/{tenantId}/devices/{deviceId}/command-results/{commandId}`
- subscribe `octopus/{tenantId}/devices/{deviceId}/commands/{operation}` or `.../commands/#`

No wildcard can escape the certificate owner's tenant and device namespace. Keep the CA
webhook service private, use HTTPS between EMQX and Octopus CA, rotate the webhook API key,
and rate-limit the endpoints at the network boundary.
