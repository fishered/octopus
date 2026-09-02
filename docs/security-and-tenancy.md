# Security and tenant isolation

## Human authentication

Use short-lived signed access JWTs (5-15 minutes) plus opaque, rotating refresh tokens.
Only the refresh-token hash is stored. A server-side Redis session record is authoritative
and contains session ID, account ID, tenant scope, token family, authentication level,
issued/idle/absolute expiry, and revocation generation. JWT validation therefore includes
signature, issuer, audience, time, session status, and account/tenant revocation generation.

Three generations have distinct meaning and are compared on every authenticated request:

- refresh generation increments on every refresh-token rotation and detects family reuse;
- account generation increments to force every account session offline;
- authorization generation increments when roles, permissions, organization scope, or resource grants change.

The JWT tenant must exactly equal the Redis session tenant. Login verifies an active
membership before creating a tenant session; a request-provided tenant ID is not authority.

Password hashing uses Argon2id with a versioned work-factor policy. TOTP secrets are
envelope-encrypted, never logged, and recovery codes are one-way hashed. Login has generic
errors, rate limits by account and network, progressive delay, security-event audit, and
risk hooks. Session renewal rotates the refresh token and detects family reuse. Forced
logout increments session/account/tenant revocation state and publishes invalidation. Account-wide
logout persists the new `account.session_generation` in PostgreSQL after advancing the Redis hot-path
value, so Redis loss or rebuild cannot make a previously revoked JWT generation valid again.
Redis restores this key with an atomic max operation, so a stale cache value can never move below the
PostgreSQL baseline. Refresh also reloads the account from PostgreSQL and requires both `ACTIVE` status
and an exact account-generation match before rotating the token; suspended and disabled accounts cannot
refresh even if a Redis session record still exists.

Self-service password changes use `PUT /api/v1/iam/accounts/me/password`, verify the current password,
reject password reuse, hash the replacement with Argon2id, and clear all intermediate character arrays.
Platform recovery uses `POST /api/v1/iam/accounts/{accountId}/password/reset`; it accepts an explicitly
chosen replacement and mandatory reason but never generates or returns a temporary credential. Both flows
atomically persist `password_changed_at`, the Argon2id hash, the new account session generation, and a
platform `security_audit_event` containing actor/session/target/time/reason metadata but no password data.
Every existing access and refresh session, including the session that performed a self-service change,
is invalid immediately.

Global account suspension, disablement, and reactivation are platform-administrator operations;
tenant administrators can suspend only memberships in their authorized organization subtree. Every
global account status change advances the account session generation, persists status and generation
atomically, and writes a platform security audit event with actor, session, old/new status, UTC time,
and mandatory reason. Consequently, access and refresh tokens issued before any account status
transition fail immediately on every service node.

TOTP follows RFC 6238 with a narrow clock window and constant-time comparison. Success
atomically advances `mfa_factor.last_accepted_counter`; the same or an older counter is
rejected across every service node to prevent replay.

TOTP provisioning is a two-phase lifecycle. `POST /api/v1/auth/totp/enrollments` requires the
current password and creates a ten-minute `PENDING` factor; its Base32 secret and `otpauth://` URI
are returned only in that response, while PostgreSQL receives only an AES-256-GCM envelope. Starting
a replacement enrollment revokes the previous pending envelope. The caller proves authenticator
possession with `POST /api/v1/auth/totp/enrollments/{factorId}/activate`; activation atomically records
the accepted RFC 6238 counter, creates ten unique recovery codes, advances/persists account session
generation, and returns the raw recovery codes exactly once with `Cache-Control: no-store`.

Recovery codes are stored exclusively as Argon2id hashes in `mfa_recovery_code`. Login accepts either
`totpCode` or `recoveryCode`, never both; a recovery code is consumed with a conditional update in the
same PostgreSQL transaction and writes `MFA_RECOVERY_CODE_USED`, so concurrent reuse succeeds at most
once. `POST /api/v1/auth/totp/remove` requires both the current password and a fresh TOTP code. Platform
recovery uses `POST /api/v1/iam/accounts/{accountId}/totp/reset` with a mandatory reason. Removal,
activation, and platform reset revoke every existing access/refresh session and write global security
audit events without secret or recovery-code material. Revocation nulls the encrypted TOTP envelope
and revokes all unused recovery-code hashes; completed enrollment secrets are never returned again.

The authentication SPI supports password+TOTP now and WebAuthn/OIDC/SAML later. Device
authentication is separate and uses mTLS certificates managed by `octopus-ca`.

## Authorization

RBAC grants actions through roles and permissions; menus are presentation metadata, not
security permissions. ABAC/data grants restrict resources by organization subtree,
facility, device, device group, or explicit resource ID.

- Platform super administrator: platform scope, always audited, optional support reason.
- Organization administrator: all actions allowed by policy within its organization subtree.
- Operator: assigned actions plus data scopes and explicit resource grants.

Organization authorization is membership-bound. JWTs contain structured organization scopes,
each carrying one organization subtree, its `ADMINISTRATOR`/`OPERATOR` level, and the permissions
assigned through that membership. Authorization never combines a permission obtained in one
membership with the organization path of another. Likewise, having an organization-admin role in
one subtree does not elevate operator memberships elsewhere in the tenant. Tenant JWTs missing the
structured scope claim are rejected rather than interpreted using the legacy independent
`permissions` and `organization_paths` sets.

Tenant roles can contain only permissions marked `tenant_assignable`. Platform-wide capabilities
such as `platform:all`, global account creation, global menu mutation, and device manufacturing
cannot be inserted into a tenant role. An operator who is allowed to administer roles may delegate
only operator roles and only permissions already held by that operator; the same hierarchy check is
applied when assigning an existing role. Organization administrators may delegate any
tenant-assignable permission inside their organization subtree. Authentication queries filter the
permission vocabulary again, so a legacy or manually inserted tenant grant cannot become a JWT
platform authority.

Authorization is deny-by-default. A request must satisfy tenant boundary, action permission,
and resource scope. Device `VIEW`, `OPERATE`, `CONFIGURE`, and `OWNER_ADMIN` are distinct.

Explicit data access is represented by an audited `ResourceGrant` attached to one active
membership. Management supports device (`VIEW`, `OPERATE`, `CONFIGURE`), facility (`VIEW`,
`CONFIGURE`), and meter (`VIEW`, `CONFIGURE`) grants. Only organization or platform administrators
may create or revoke them. The administrator must control both the membership organization and the
target resource's organization path; RLS independently enforces the tenant boundary. Duplicate
grants are idempotent. Revocation is a tombstone with actor, timestamp, and reason rather than a
hard delete. Every mutation writes `security_audit_event` and advances the affected account's
authorization generation, immediately invalidating JWTs issued with the previous grant set.

Membership and role removal follow the same fail-closed invalidation rule. Revoking a role assignment
or suspending an active membership first advances the affected account's tenant authorization
generation, then performs the tenant-scoped mutation and writes a `security_audit_event` containing
the actor, session, target IDs, UTC timestamp, and mandatory reason. A suspended membership is
excluded from login, refresh, organization scopes, permissions, and resource-grant loading; no
service waits for the access JWT's normal expiry before enforcing the removal.

Changing a role's permission set or retiring the role enumerates every active membership assigned to
that role and advances each affected account's authorization generation before the tenant transaction
commits the role mutation. The transaction persists all generated baselines and one role audit event,
so every node rejects pre-change JWTs immediately. Organization-administrator roles carry implicit
full permission only inside their membership subtree and therefore cannot contain explicit permission
rows; operator roles require an explicit, non-empty tenant-assignable permission set.

IAM management reads are organization-scoped rather than tenant-wide by default: role and membership
lists require `role:view` or `account:view` in the requested organization path. A suspended membership
may be reactivated only through an audited `account:manage` operation in that same path. Reactivation
advances the tenant authorization generation before restoring access, so sessions and JWTs created
against the suspended state cannot become valid merely because the membership changed back to active.

## Isolation layers

1. The authenticated principal establishes `TenantContext`; caller headers never do.
2. Application commands and repository methods carry tenant scope explicitly.
3. MyBatis-Plus adds `tenant_id` to tenant-owned tables and blocks missing scope.
4. PostgreSQL row-level security is enabled and forced for high-risk tenant tables; the runtime database role must not have `BYPASSRLS`.
5. Redis keys begin `octopus:{environment}:{tenantId}:...` and Kafka records carry the tenant in the signed/event envelope.
6. InfluxDB uses tenant-aware bucket/measurement/tag policy with query guards and quotas.
7. Object keys are rooted below a tenant prefix and normalized against traversal.

Global tables (unit catalog, permission definitions, device type templates) are explicitly
allowlisted. Bypass is not a boolean request parameter; only a verified platform principal
or controlled system job can create a platform scope, and every use is audited.

Tenant transactions use a dedicated transaction manager that calls
`set_config('app.tenant_id', tenantId, true)` on the transaction-bound JDBC connection.
Platform/bootstrap repositories use a separately named transaction manager and require
explicit audit. Missing tenant context fails before tenant SQL executes.
