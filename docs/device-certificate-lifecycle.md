# Device certificate lifecycle

Octopus uses a two-stage identity so a factory credential is not a permanent production
credential. The numbered flow is the target lifecycle; the v1.0 implementation boundary
is stated explicitly below.

1. Manufacturing creates a device identity and hardware-bound key where possible (TPM,
   secure element, or protected keystore). The private key never leaves the device.
2. The factory CA signs a short-scope bootstrap certificate or one-time enrollment claim.
   The manufacturing record stores serial, public-key fingerprint, batch, model, and status.
3. On first connection, `octopus-ca` validates the bootstrap chain, claim, nonce proof,
   model policy, and unclaimed status. Claiming binds the device to exactly one tenant.
4. The device creates a new operational key and CSR. The tenant/device intermediate CA
   issues a short-lived operational certificate with device and tenant identity in SAN.
5. MQTT authorizes the certificate to a narrow topic namespace. Broker ACLs derive tenant
   and device from the verified certificate, never the payload or client-supplied username.
6. Renewal starts before expiry using the current operational certificate and proof of
   possession. Rotation overlaps briefly, then revokes the old certificate.
7. Transfer, compromise, decommission, and factory reset are explicit workflows. Revocation
   is pushed to control nodes and broker authorization caches; CRL/OCSP data is durable.

Root CA keys remain offline. Issuing intermediate keys use an HSM/KMS-backed `SigningPort`.
The service database stores certificates, public keys, status, and audit events, never CA
private-key material. All issuance and revocation operations require idempotency keys and
append-only audit evidence. Clock-skew policy is explicit because devices may boot without
trusted wall-clock time.

The initial protocol should follow EST-style CSR enrollment over mTLS. AWS IoT migration
maps the same lifecycle port to fleet provisioning/JITP and AWS certificate policies; the
domain identity and claim state do not depend on AWS resource identifiers.

## Implemented baseline

- Manufacturing creates a `ca_device_identity` and a 256-bit one-time bootstrap token.
  The raw token is returned once; PostgreSQL stores only an HMAC-SHA256 digest and expiry.
- Claiming requires a verified tenant JWT, the logical `deviceId`, and the bootstrap token.
  A platform-scoped RLS transaction locks the identity, atomically consumes the token, and
  binds exactly one tenant and logical device.
- Certificate issuance verifies the PKCS#10 CSR signature, requires an idempotency key,
  adds tenant, logical-device, and identity URIs to SAN, and persists the certificate and identity update in
  one tenant transaction.
- Issuance returns a stable `certificateId` UUID used by `device_actual.certificate_id`;
  retries with the same idempotency key and CSR return the same certificate. Reusing the key
  with a different CSR returns a conflict.
- Rotation marks the previous active certificate `SUPERSEDED`. Revocation updates both the
  identity and its current certificate, including timestamp and reason.
- PostgreSQL FORCE RLS separates platform manufacturing/bootstrap access from tenant
  issuance/revocation. Platform support access to a tenant requires an explicit tenant ID
  and support reason.

The `local-pkcs12` signing adapter exists only for private development environments.
Production must supply `CertificateSigningPort` from HSM/KMS-backed infrastructure. The
default deployment keeps signing disabled so it cannot silently fall back to a filesystem
private key.

## v1.0 limitations

The v1.0 revocation state is enforced by the Octopus EMQX HTTP authorizer, but CRL/OCSP
publication, automated renewal, HSM/KMS signing, append-only CA audit evidence, and
revocation fan-out/cache invalidation are not yet implemented. These are release gates for
the planned 1.1 production-PKI increment; do not present the local PKCS#12 mode as a
production trust service.
