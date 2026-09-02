# Object storage

## Boundary and modules

Business code depends on the provider-neutral `ObjectStorage` port from
`octopus-common-storage`. The same artifact contains the local-filesystem adapter and has
no AWS dependency. `octopus-common-storage-s3` is a separate Nexus-publishable adapter so
local-only services do not inherit the AWS SDK dependency graph.

`octopus-mgmt` owns the Spring wiring. The selected provider is an infrastructure concern;
domain and application code must not branch on Local versus S3 or expose provider-specific
bucket paths.

## Tenant isolation and keys

Every operation requires a `TenantId`. Physical paths always include the immutable tenant
UUID:

```text
Local: <root>/<tenantUuid>/<logicalKey>
S3:    <prefix>/<tenantUuid>/<logicalKey>
```

The tenant ID must come from the authenticated tenant context, never from an unchecked HTTP
parameter. This prefix is defense in depth; authorization and PostgreSQL tenant/resource
policy checks still happen before an artifact operation.

`ObjectKey` accepts relative forward-slash paths only. It rejects absolute paths,
backslashes, empty segments, `.`/`..` segments, control characters, non-portable filesystem
characters/reserved names, and keys above 900 UTF-8 bytes. The S3 adapter also rejects the
final prefixed key above S3's 1024-byte key limit. Logical keys should be opaque identifiers
plus a safe extension rather than a raw user filename.

## Provider selection

Local storage is the application default:

```yaml
octopus:
  storage:
    type: local
    local:
      root: ./data/objects
```

S3 configuration:

```yaml
octopus:
  storage:
    type: s3
    s3:
      bucket: octopus-production-artifacts
      region: us-east-1
      prefix: objects
      endpoint: ""
      path-style: false
      encryption: S3_MANAGED
      kms-key-id: ""
```

Environment mappings are:

| Property | Environment variable | Default |
| --- | --- | --- |
| `octopus.storage.type` | `OCTOPUS_STORAGE_TYPE` | `local` |
| `octopus.storage.local.root` | `OCTOPUS_STORAGE_LOCAL_ROOT` | `./data/objects` |
| `octopus.storage.s3.bucket` | `OCTOPUS_STORAGE_S3_BUCKET` | required for S3 |
| `octopus.storage.s3.region` | `OCTOPUS_STORAGE_S3_REGION` | `us-east-1` |
| `octopus.storage.s3.prefix` | `OCTOPUS_STORAGE_S3_PREFIX` | empty |
| `octopus.storage.s3.endpoint` | `OCTOPUS_STORAGE_S3_ENDPOINT` | AWS endpoint resolution |
| `octopus.storage.s3.path-style` | `OCTOPUS_STORAGE_S3_PATH_STYLE` | `false` |
| `octopus.storage.s3.encryption` | `OCTOPUS_STORAGE_S3_ENCRYPTION` | `S3_MANAGED` |
| `octopus.storage.s3.kms-key-id` | `OCTOPUS_STORAGE_S3_KMS_KEY_ID` | required for `KMS` |

The supported server-side encryption modes are:

- `NONE`: no encryption header is added; bucket policy may still enforce encryption.
- `S3_MANAGED`: requests S3-managed AES-256 encryption.
- `KMS`: requests AWS KMS encryption and requires a key ID, alias, or ARN.

The SDK default credentials chain is used. On Kubernetes, prefer workload identity/IRSA
and grant only bucket/prefix and KMS permissions required by the service account. No Octopus
property accepts an access key or secret key. For an S3-compatible provider that cannot use
workload identity, inject SDK-standard credential variables from a Kubernetes Secret rather
than placing credentials in a ConfigMap or `application.yml`.

Set `endpoint` and usually `path-style=true` for MinIO or another S3-compatible service.
Endpoint overrides must be absolute HTTP(S) URIs.

## Durability and switching

Local storage is suitable for development and single-node deployments. A container
filesystem or `emptyDir` is ephemeral and each replica sees different files. A Kubernetes
deployment that selects Local therefore needs a deliberately provisioned durable volume;
multi-replica use additionally needs shared access semantics and measured consistency.
Production Kubernetes defaults to S3 in the base manifest.

Changing `octopus.storage.type` changes where new reads and writes are directed; it does not
copy existing objects. A migration must inventory source objects per tenant, copy and verify
content, preserve logical keys, and only then switch traffic. Dual-write or fallback-read
behavior is intentionally not hidden in the adapter and requires a separately designed,
observable migration workflow.

S3 ETags are retained as provider metadata but are not treated as universal content hashes,
especially for multipart uploads or encrypted objects. The local adapter currently returns a
SHA-256 digest.
