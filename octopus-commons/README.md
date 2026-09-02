# Octopus Commons

These modules are separately versioned Nexus artifacts. Keep dependency direction small:

- `core`: identifiers, time rules, result/error primitives.
- `tenant`: framework-neutral tenant execution context.
- `security`: framework-neutral authenticated principal and authorization vocabulary.
- `persistence`: MyBatis-Plus tenant enforcement and persistence conventions.
- `storage`: provider-neutral object-storage port and lightweight local adapter.
- `storage-s3`: AWS SDK v2 S3 adapter with tenant-prefixed keys and optional KMS encryption.

Do not add business aggregates, service DTOs, or convenience dependency bundles here.
