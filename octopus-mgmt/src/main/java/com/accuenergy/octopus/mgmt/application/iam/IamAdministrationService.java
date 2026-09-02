package com.accuenergy.octopus.mgmt.application.iam;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AuthenticationFailedException;
import com.accuenergy.octopus.mgmt.application.identity.PasswordVerifier;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedAccount;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedResourceGrant;
import com.accuenergy.octopus.mgmt.domain.identity.NavigationMenu;
import com.accuenergy.octopus.mgmt.domain.identity.PermissionDefinition;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class IamAdministrationService {
    private final IamAdministrationRepository repository;
    private final PasswordHasher passwords;
    private final PasswordVerifier passwordVerifier;
    private final AuthorizationPolicy authorization;
    private final AuthorizationGenerationRegistry generations;
    private final AccountSessionGenerationRegistry accountSessionGenerations;
    private final Clock clock;

    public IamAdministrationService(IamAdministrationRepository repository, PasswordHasher passwords,
            PasswordVerifier passwordVerifier,
            AuthorizationPolicy authorization, AuthorizationGenerationRegistry generations,
            AccountSessionGenerationRegistry accountSessionGenerations, Clock clock) {
        this.repository = repository;
        this.passwords = passwords;
        this.passwordVerifier = passwordVerifier;
        this.authorization = authorization;
        this.generations = generations;
        this.accountSessionGenerations = accountSessionGenerations;
        this.clock = clock;
    }

    public ManagedAccount createAccount(AuthenticatedPrincipal principal, CreateAccount command) {
        if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            throw new IamAccessDeniedException();
        }
        if (repository.accountIdentityExists(command.loginName(), command.email())) {
            throw new IllegalArgumentException("Login name or email already exists");
        }
        char[] password = command.password();
        try {
            ManagedAccount account = ManagedAccount.create(UUID.randomUUID(), command.loginName(), command.email(),
                    command.phoneE164(), passwords.hash(password), clock.instant());
            repository.insertAccount(account);
            return account;
        } finally {
            Arrays.fill(password, '\0');
            command.clearPassword();
        }
    }

    public AccountStatusChange changeAccountStatus(AuthenticatedPrincipal principal, UUID accountId,
                                                   ManagedAccount.Status status, String reason) {
        if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            throw new IamAccessDeniedException();
        }
        String normalizedReason = normalizeRevokeReason(reason);
        IamAdministrationRepository.AccountSecurityContext account = repository.findAccountSecurity(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account"));
        if (account.status() == status) {
            return new AccountStatusChange(accountId, status, account.sessionGeneration());
        }
        long nextGeneration = accountSessionGenerations.revokeAll(accountId, account.sessionGeneration());
        repository.changeAccountStatus(account, status, nextGeneration, principal.accountId(),
                principal.sessionId(), normalizedReason, clock.instant());
        return new AccountStatusChange(accountId, status, nextGeneration);
    }

    public PasswordChange changeOwnPassword(AuthenticatedPrincipal principal, ChangeOwnPassword command) {
        IamAdministrationRepository.AccountSecurityContext account = repository
                .findAccountSecurity(principal.accountId())
                .orElseThrow(IamAdministrationService::invalidCredentials);
        char[] currentPassword = command.currentPassword();
        char[] newPassword = command.newPassword();
        try {
            if (account.status() != ManagedAccount.Status.ACTIVE
                    || !passwordVerifier.matches(currentPassword, account.passwordHash())) {
                throw invalidCredentials();
            }
            requireDifferentPassword(newPassword, account.passwordHash());
            return persistPasswordChange(account, newPassword, principal.accountId(), principal.sessionId(),
                    "PASSWORD_CHANGED", "Self-service password change");
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword, '\0');
            command.clearPasswords();
        }
    }

    public PasswordChange resetAccountPassword(AuthenticatedPrincipal principal, UUID accountId,
                                               ResetAccountPassword command) {
        if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            throw new IamAccessDeniedException();
        }
        String reason = normalizeRevokeReason(command.reason());
        IamAdministrationRepository.AccountSecurityContext account = repository.findAccountSecurity(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account"));
        char[] newPassword = command.newPassword();
        try {
            requireDifferentPassword(newPassword, account.passwordHash());
            return persistPasswordChange(account, newPassword, principal.accountId(), principal.sessionId(),
                    "PASSWORD_RESET_BY_PLATFORM", reason);
        } finally {
            Arrays.fill(newPassword, '\0');
            command.clearPassword();
        }
    }

    private PasswordChange persistPasswordChange(IamAdministrationRepository.AccountSecurityContext account,
                                                  char[] newPassword, UUID actorAccountId, UUID sessionId,
                                                  String eventType, String reason) {
        String passwordHash = passwords.hash(newPassword);
        long nextGeneration = accountSessionGenerations.revokeAll(
                account.accountId(), account.sessionGeneration());
        repository.changePassword(account, passwordHash, nextGeneration, actorAccountId, sessionId,
                eventType, reason, clock.instant());
        return new PasswordChange(account.accountId(), nextGeneration);
    }

    private void requireDifferentPassword(char[] newPassword, String currentPasswordHash) {
        if (passwordVerifier.matches(newPassword, currentPasswordHash)) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
    }

    private static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("Invalid credentials");
    }

    public UUID addMembership(AuthenticatedPrincipal principal, UUID accountId, UUID organizationId) {
        TenantId tenantId = currentTenant();
        IamAdministrationRepository.OrganizationContext organization = repository.findOrganization(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        requireAllowed(principal, tenantId, "account:manage", organizationId, organization.path());
        if (repository.membershipExists(accountId, organizationId)) {
            throw new IllegalArgumentException("Account is already a member of the organization");
        }
        return repository.insertMembership(tenantId.value(), accountId, organizationId, clock.instant());
    }

    public UUID createRole(AuthenticatedPrincipal principal, CreateRole command) {
        TenantId tenantId = currentTenant();
        IamAdministrationRepository.OrganizationContext organization = repository.findOrganization(command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        requireAllowed(principal, tenantId, "role:manage", command.organizationId(), organization.path());
        if (command.code() == null || !command.code().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Role code is invalid");
        }
        if (command.displayName() == null || command.displayName().isBlank()
                || command.displayName().length() > 200) {
            throw new IllegalArgumentException("Role display name is invalid");
        }
        validateRolePermissions(command.roleType(), command.permissionCodes());
        requireDelegatableRoleType(principal, organization.path(), command.roleType());
        if (repository.roleCodeExists(command.code())) throw new IllegalArgumentException("Role code already exists");
        Set<String> assignable = repository.findTenantAssignablePermissions(command.permissionCodes());
        if (!assignable.equals(command.permissionCodes())) {
            throw new IllegalArgumentException("Role contains unknown or platform-only permissions");
        }
        requireDelegatablePermissions(principal, organization.path(), command.permissionCodes());
        return repository.insertRole(tenantId.value(), command.organizationId(), command.code(),
                command.displayName(), command.roleType(), command.permissionCodes(), clock.instant());
    }

    public List<IamAdministrationRepository.RoleView> listRoles(AuthenticatedPrincipal principal,
                                                                 UUID organizationId) {
        TenantId tenantId = currentTenant();
        var organization = repository.findOrganization(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        requireAllowed(principal, tenantId, "role:view", organizationId, organization.path());
        return List.copyOf(repository.listRoles(organizationId));
    }

    public List<IamAdministrationRepository.MembershipView> listMemberships(AuthenticatedPrincipal principal,
                                                                             UUID organizationId) {
        TenantId tenantId = currentTenant();
        var organization = repository.findOrganization(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        requireAllowed(principal, tenantId, "account:view", organizationId, organization.path());
        return List.copyOf(repository.listMemberships(organizationId));
    }

    public long assignRole(AuthenticatedPrincipal principal, UUID membershipId, UUID roleId) {
        TenantId tenantId = currentTenant();
        var membership = repository.findMembership(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership"));
        var role = repository.findRole(roleId).orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        if (!membership.organizationId().equals(role.organizationId())) {
            throw new IllegalArgumentException("Role and membership belong to different organizations");
        }
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        requireDelegatableRole(principal, membership.organizationPath(), role);
        if (repository.assignmentExists(membershipId, roleId)) return membership.authorizationGeneration();
        long nextGeneration = generations.revoke(membership.accountId(), Optional.of(tenantId.value()),
                membership.authorizationGeneration());
        repository.assignRole(tenantId.value(), membershipId, roleId, membership.accountId(),
                nextGeneration, clock.instant());
        return nextGeneration;
    }

    public long revokeRole(AuthenticatedPrincipal principal, UUID membershipId, UUID roleId, String reason) {
        TenantId tenantId = currentTenant();
        String normalizedReason = normalizeRevokeReason(reason);
        var membership = repository.findMembership(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership"));
        var role = repository.findRole(roleId).orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        if (!membership.organizationId().equals(role.organizationId())) {
            throw new IllegalArgumentException("Role and membership belong to different organizations");
        }
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        requireDelegatableRole(principal, membership.organizationPath(), role);
        if (!repository.assignmentExists(membershipId, roleId)) {
            return membership.authorizationGeneration();
        }
        long nextGeneration = generations.revoke(membership.accountId(), Optional.of(tenantId.value()),
                membership.authorizationGeneration());
        repository.revokeRole(tenantId.value(), membershipId, roleId, principal.accountId(),
                principal.sessionId(), membership.accountId(), nextGeneration, normalizedReason, clock.instant());
        return nextGeneration;
    }

    public long suspendMembership(AuthenticatedPrincipal principal, UUID membershipId, String reason) {
        TenantId tenantId = currentTenant();
        String normalizedReason = normalizeRevokeReason(reason);
        var membership = repository.findMembership(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown active membership"));
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        long nextGeneration = generations.revoke(membership.accountId(), Optional.of(tenantId.value()),
                membership.authorizationGeneration());
        repository.suspendMembership(tenantId.value(), membership, principal.accountId(),
                principal.sessionId(), nextGeneration, normalizedReason, clock.instant());
        return nextGeneration;
    }

    public long activateMembership(AuthenticatedPrincipal principal, UUID membershipId, String reason) {
        TenantId tenantId = currentTenant();
        String normalizedReason = normalizeRevokeReason(reason);
        var membership = repository.findMembershipLifecycle(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership"));
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        if (membership.status() != IamAdministrationRepository.MembershipStatus.SUSPENDED) {
            return membership.authorizationGeneration();
        }
        long nextGeneration = generations.revoke(membership.accountId(), Optional.of(tenantId.value()),
                membership.authorizationGeneration());
        repository.activateMembership(tenantId.value(), membership, principal.accountId(),
                principal.sessionId(), nextGeneration, normalizedReason, clock.instant());
        return nextGeneration;
    }

    public RoleAuthorizationChange replaceRolePermissions(AuthenticatedPrincipal principal, UUID roleId,
                                                           Set<String> permissionCodes, String reason) {
        TenantId tenantId = currentTenant();
        String normalizedReason = normalizeRevokeReason(reason);
        var role = repository.findRole(roleId).orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        var organization = repository.findOrganization(role.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        requireAllowed(principal, tenantId, "role:manage", role.organizationId(), organization.path());
        validateRolePermissions(role.roleType(), permissionCodes);
        requireDelegatableRoleType(principal, organization.path(), role.roleType());
        requireDelegatablePermissions(principal, organization.path(), permissionCodes);
        Set<String> assignable = repository.findTenantAssignablePermissions(permissionCodes);
        if (!assignable.equals(permissionCodes)) {
            throw new IllegalArgumentException("Role contains unknown or platform-only permissions");
        }
        if (role.permissionCodes().equals(permissionCodes)) {
            return new RoleAuthorizationChange(roleId, 0);
        }
        List<IamAdministrationRepository.AccountAuthorizationChange> changes = authorizationChanges(
                tenantId, repository.findActiveRoleMemberships(roleId));
        repository.replaceRolePermissions(tenantId.value(), role, permissionCodes, changes,
                principal.accountId(), principal.sessionId(), normalizedReason, clock.instant());
        return new RoleAuthorizationChange(roleId, changes.size());
    }

    public RoleAuthorizationChange retireRole(AuthenticatedPrincipal principal, UUID roleId, String reason) {
        TenantId tenantId = currentTenant();
        String normalizedReason = normalizeRevokeReason(reason);
        var role = repository.findRole(roleId).orElseThrow(() -> new IllegalArgumentException("Unknown active role"));
        var organization = repository.findOrganization(role.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        requireAllowed(principal, tenantId, "role:manage", role.organizationId(), organization.path());
        requireDelegatableRole(principal, organization.path(), role);
        List<IamAdministrationRepository.AccountAuthorizationChange> changes = authorizationChanges(
                tenantId, repository.findActiveRoleMemberships(roleId));
        repository.retireRole(tenantId.value(), role, changes, principal.accountId(), principal.sessionId(),
                normalizedReason, clock.instant());
        return new RoleAuthorizationChange(roleId, changes.size());
    }

    private List<IamAdministrationRepository.AccountAuthorizationChange> authorizationChanges(
            TenantId tenantId, List<IamAdministrationRepository.MembershipContext> memberships) {
        return memberships.stream().map(membership -> new IamAdministrationRepository.AccountAuthorizationChange(
                membership.accountId(), generations.revoke(membership.accountId(),
                        Optional.of(tenantId.value()), membership.authorizationGeneration())))
                .toList();
    }

    public List<PermissionDefinition> listPermissionCatalog(AuthenticatedPrincipal principal) {
        requireGlobalReadPermission(principal, "permission:view");
        return List.copyOf(repository.listTenantAssignablePermissions());
    }

    public List<NavigationMenu> listVisibleMenus(AuthenticatedPrincipal principal) {
        requireGlobalReadPermission(principal, "menu:view");
        if (principal.type() == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            return List.copyOf(repository.listActiveMenus());
        }
        return repository.listActiveMenus().stream()
                .filter(menu -> menu.isVisibleTo(principal.permissions()))
                .toList();
    }

    public ResourceGrantChange grantResourceAccess(AuthenticatedPrincipal principal, UUID membershipId,
                                                    GrantResourceAccess command) {
        TenantId tenantId = currentTenant();
        requireAnyGrantAdministrator(principal);
        IamAdministrationRepository.MembershipContext membership = repository.findMembership(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership"));
        requireGrantAdministrator(principal, membership.organizationPath());
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        IamAdministrationRepository.GrantTargetContext target = repository
                .findGrantTarget(command.resourceType(), command.resourceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive grant target"));
        requireResourceAllowed(principal, tenantId, command, target);
        Optional<ManagedResourceGrant> existing = repository.findActiveResourceGrant(membershipId,
                command.resourceType(), command.resourceId(), command.action());
        if (existing.isPresent()) {
            return new ResourceGrantChange(existing.orElseThrow(), membership.authorizationGeneration());
        }
        long nextGeneration = generations.revoke(membership.accountId(), Optional.of(tenantId.value()),
                membership.authorizationGeneration());
        ManagedResourceGrant grant = repository.upsertResourceGrant(tenantId.value(), membershipId,
                command.resourceType(), command.resourceId(), command.action(), principal.accountId(),
                principal.sessionId(), membership.accountId(), nextGeneration, clock.instant());
        return new ResourceGrantChange(grant, nextGeneration);
    }

    public List<ManagedResourceGrant> listResourceGrants(AuthenticatedPrincipal principal, UUID membershipId) {
        TenantId tenantId = currentTenant();
        requireAnyGrantAdministrator(principal);
        IamAdministrationRepository.MembershipContext membership = repository.findMembership(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership"));
        requireGrantAdministrator(principal, membership.organizationPath());
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        return List.copyOf(repository.listActiveResourceGrants(membershipId));
    }

    public ResourceGrantChange revokeResourceAccess(AuthenticatedPrincipal principal, UUID grantId,
                                                     String reason) {
        TenantId tenantId = currentTenant();
        requireAnyGrantAdministrator(principal);
        String normalizedReason = normalizeRevokeReason(reason);
        ManagedResourceGrant grant = repository.findActiveResourceGrant(grantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown active resource grant"));
        IamAdministrationRepository.MembershipContext membership = repository
                .findMembership(grant.membershipId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership"));
        requireGrantAdministrator(principal, membership.organizationPath());
        requireAllowed(principal, tenantId, "account:manage", membership.organizationId(),
                membership.organizationPath());
        long nextGeneration = generations.revoke(membership.accountId(), Optional.of(tenantId.value()),
                membership.authorizationGeneration());
        repository.revokeResourceGrant(grant, principal.accountId(), principal.sessionId(),
                membership.accountId(), nextGeneration, normalizedReason, clock.instant());
        return new ResourceGrantChange(grant, nextGeneration);
    }

    private static void requireDelegatableRole(AuthenticatedPrincipal principal, String organizationPath,
                                                IamAdministrationRepository.RoleContext role) {
        requireDelegatableRoleType(principal, organizationPath, role.roleType());
        requireDelegatablePermissions(principal, organizationPath, role.permissionCodes());
    }

    private static void requireDelegatableRoleType(AuthenticatedPrincipal principal, String organizationPath,
                                                    IamAdministrationRepository.RoleType roleType) {
        if (!hasAdministratorScope(principal, organizationPath)
                && roleType != IamAdministrationRepository.RoleType.OPERATOR) {
            throw new IamAccessDeniedException();
        }
    }

    private static void requireDelegatablePermissions(AuthenticatedPrincipal principal, String organizationPath,
                                                       Set<String> permissions) {
        if (hasAdministratorScope(principal, organizationPath)) return;
        boolean holdsEveryPermissionInTargetScope = permissions.stream().allMatch(permission ->
                principal.organizationScopes().stream().anyMatch(scope -> scope.allows(permission, organizationPath)));
        if (!holdsEveryPermissionInTargetScope) {
            throw new IamAccessDeniedException();
        }
    }

    private static void requireGrantAdministrator(AuthenticatedPrincipal principal, String organizationPath) {
        if (!hasAdministratorScope(principal, organizationPath)) throw new IamAccessDeniedException();
    }

    private static void requireAnyGrantAdministrator(AuthenticatedPrincipal principal) {
        if (principal.type() == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) return;
        boolean hasAdministratorScope = principal.organizationScopes().stream().anyMatch(scope ->
                scope.accessLevel() == OrganizationAccessScope.AccessLevel.ADMINISTRATOR);
        if (!hasAdministratorScope) throw new IamAccessDeniedException();
    }

    private static boolean hasAdministratorScope(AuthenticatedPrincipal principal, String organizationPath) {
        if (principal.type() == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) return true;
        return principal.organizationScopes().stream().anyMatch(scope ->
                scope.accessLevel() == OrganizationAccessScope.AccessLevel.ADMINISTRATOR
                        && scope.contains(organizationPath));
    }

    private void requireResourceAllowed(AuthenticatedPrincipal principal, TenantId tenantId,
                                        GrantResourceAccess command,
                                        IamAdministrationRepository.GrantTargetContext target) {
        ManagedResourceGrant candidate = ManagedResourceGrant.active(UUID.randomUUID(), tenantId.value(),
                UUID.randomUUID(), command.resourceType(), command.resourceId(), command.action(),
                principal.accountId(), clock.instant());
        if (!authorization.isAllowed(principal, candidate.permissionCode(), command.action(),
                new ProtectedResource(tenantId, command.resourceType().code(), command.resourceId(),
                        Optional.of(target.organizationPath())))) {
            throw new IamAccessDeniedException();
        }
    }

    private static String normalizeRevokeReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("Revocation reason is invalid");
        }
        return reason.strip();
    }

    private static void validateRolePermissions(IamAdministrationRepository.RoleType roleType,
                                                Set<String> permissionCodes) {
        if (roleType == IamAdministrationRepository.RoleType.ORGANIZATION_ADMIN
                && !permissionCodes.isEmpty()) {
            throw new IllegalArgumentException("Organization administrator roles use implicit subtree permissions");
        }
        if (roleType == IamAdministrationRepository.RoleType.OPERATOR && permissionCodes.isEmpty()) {
            throw new IllegalArgumentException("Operator roles require at least one permission");
        }
    }

    private static void requireGlobalReadPermission(AuthenticatedPrincipal principal, String permission) {
        if (principal.type() == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) return;
        TenantId tenantId = currentTenant();
        if (!principal.tenantId().orElseThrow().equals(tenantId)
                || (principal.type() == AuthenticatedPrincipal.PrincipalType.OPERATOR
                    && !principal.permissions().contains(permission))) {
            throw new IamAccessDeniedException();
        }
    }

    private void requireAllowed(AuthenticatedPrincipal principal, TenantId tenantId, String permission,
                                UUID organizationId, String path) {
        if (!authorization.isAllowed(principal, permission, ResourceAction.OWNER_ADMIN,
                new ProtectedResource(tenantId, "organization", organizationId, Optional.of(path)))) {
            throw new IamAccessDeniedException();
        }
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
    }

    public record CreateAccount(String loginName, String email, String phoneE164, char[] password) {
        public CreateAccount { password = password.clone(); }
        @Override public char[] password() { return password.clone(); }
        public void clearPassword() { Arrays.fill(password, '\0'); }
    }
    public record ChangeOwnPassword(char[] currentPassword, char[] newPassword) {
        public ChangeOwnPassword {
            currentPassword = currentPassword.clone();
            newPassword = newPassword.clone();
        }
        @Override public char[] currentPassword() { return currentPassword.clone(); }
        @Override public char[] newPassword() { return newPassword.clone(); }
        public void clearPasswords() {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }
    public record ResetAccountPassword(char[] newPassword, String reason) {
        public ResetAccountPassword { newPassword = newPassword.clone(); }
        @Override public char[] newPassword() { return newPassword.clone(); }
        public void clearPassword() { Arrays.fill(newPassword, '\0'); }
    }
    public record CreateRole(UUID organizationId, String code, String displayName,
                             IamAdministrationRepository.RoleType roleType, Set<String> permissionCodes) {
        public CreateRole { permissionCodes = Set.copyOf(permissionCodes); }
    }
    public record GrantResourceAccess(ManagedResourceGrant.ResourceType resourceType,
                                      UUID resourceId, ResourceAction action) {
        public GrantResourceAccess {
            java.util.Objects.requireNonNull(resourceType, "resourceType");
            java.util.Objects.requireNonNull(resourceId, "resourceId");
            java.util.Objects.requireNonNull(action, "action");
            if (!resourceType.allowedActions().contains(action)) {
                throw new IllegalArgumentException("Action is not valid for resource type");
            }
        }
    }
    public record ResourceGrantChange(ManagedResourceGrant grant, long authorizationGeneration) {
        public ResourceGrantChange {
            java.util.Objects.requireNonNull(grant, "grant");
            if (authorizationGeneration < 0) {
                throw new IllegalArgumentException("authorizationGeneration must be non-negative");
            }
        }
    }
    public record RoleAuthorizationChange(UUID roleId, int affectedAccountCount) {
        public RoleAuthorizationChange {
            java.util.Objects.requireNonNull(roleId, "roleId");
            if (affectedAccountCount < 0) {
                throw new IllegalArgumentException("affectedAccountCount must be non-negative");
            }
        }
    }
    public record AccountStatusChange(UUID accountId, ManagedAccount.Status status, long sessionGeneration) {
        public AccountStatusChange {
            java.util.Objects.requireNonNull(accountId, "accountId");
            java.util.Objects.requireNonNull(status, "status");
            if (sessionGeneration < 0) throw new IllegalArgumentException("sessionGeneration must be non-negative");
        }
    }
    public record PasswordChange(UUID accountId, long sessionGeneration) {
        public PasswordChange {
            java.util.Objects.requireNonNull(accountId, "accountId");
            if (sessionGeneration < 0) throw new IllegalArgumentException("sessionGeneration must be non-negative");
        }
    }
}
