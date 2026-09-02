package com.accuenergy.octopus.mgmt.application.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AuthenticationFailedException;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedAccount;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedResourceGrant;
import com.accuenergy.octopus.mgmt.domain.identity.NavigationMenu;
import com.accuenergy.octopus.mgmt.domain.identity.PermissionDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IamAdministrationServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final MemoryRepository repository = new MemoryRepository();
    private final MemoryGenerationRegistry generations = new MemoryGenerationRegistry();
    private final MemoryAccountGenerationRegistry accountGenerations = new MemoryAccountGenerationRegistry();
    private final IamAdministrationService service = new IamAdministrationService(repository,
            password -> "$argon2id$CaseSensitiveHash",
            (presented, encoded) -> encoded.equals("$argon2id$current")
                    && Arrays.equals(presented, "current-password".toCharArray()),
            new AuthorizationPolicy(), generations, accountGenerations,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void platformCreatesGlobalAccountWithoutCorruptingPasswordHash() {
        var command = new IamAdministrationService.CreateAccount("User.One", "USER@example.com", null,
                "a-strong-password".toCharArray());
        ManagedAccount account = service.createAccount(platform(), command);
        assertEquals("user.one", account.loginName());
        assertEquals("$argon2id$CaseSensitiveHash", account.passwordHash());
        assertTrue(new String(command.password()).chars().allMatch(value -> value == 0));
    }

    @Test
    void platformSuspendsAccountAndPersistsSessionInvalidation() {
        UUID accountId = UUID.randomUUID();
        repository.accountSecurity = new IamAdministrationRepository.AccountSecurityContext(
                accountId, "$argon2id$current", ManagedAccount.Status.ACTIVE, 5);

        IamAdministrationService.AccountStatusChange result = service.changeAccountStatus(
                platform(), accountId, ManagedAccount.Status.SUSPENDED, "Security investigation");

        assertEquals(ManagedAccount.Status.SUSPENDED, result.status());
        assertEquals(6, result.sessionGeneration());
        assertEquals(6, repository.persistedAccountSessionGeneration);
        assertEquals("Security investigation", repository.lastLifecycleReason);
        assertThrows(IamAccessDeniedException.class, () -> service.changeAccountStatus(
                operator("/root", "account:manage"), accountId,
                ManagedAccount.Status.ACTIVE, "Attempted tenant override"));
    }

    @Test
    void selfServicePasswordChangeVerifiesCurrentPasswordAndRevokesEverySession() {
        UUID accountId = UUID.randomUUID();
        repository.accountSecurity = new IamAdministrationRepository.AccountSecurityContext(
                accountId, "$argon2id$current", ManagedAccount.Status.ACTIVE, 7);
        var command = new IamAdministrationService.ChangeOwnPassword(
                "current-password".toCharArray(), "a-new-strong-password".toCharArray());

        IamAdministrationService.PasswordChange result = service.changeOwnPassword(
                platform(accountId), command);

        assertEquals(accountId, result.accountId());
        assertEquals(8, result.sessionGeneration());
        assertEquals("$argon2id$CaseSensitiveHash", repository.persistedPasswordHash);
        assertEquals("PASSWORD_CHANGED", repository.lastPasswordEventType);
        assertEquals(8, repository.persistedAccountSessionGeneration);
        assertTrue(new String(command.currentPassword()).chars().allMatch(value -> value == 0));
        assertTrue(new String(command.newPassword()).chars().allMatch(value -> value == 0));
    }

    @Test
    void selfServicePasswordChangeRejectsWrongCurrentPasswordWithoutChangingState() {
        UUID accountId = UUID.randomUUID();
        repository.accountSecurity = new IamAdministrationRepository.AccountSecurityContext(
                accountId, "$argon2id$current", ManagedAccount.Status.ACTIVE, 2);
        var command = new IamAdministrationService.ChangeOwnPassword(
                "wrong-password".toCharArray(), "a-new-strong-password".toCharArray());

        assertThrows(AuthenticationFailedException.class,
                () -> service.changeOwnPassword(platform(accountId), command));

        assertEquals(0, accountGenerations.generation);
        assertEquals(null, repository.persistedPasswordHash);
        assertTrue(new String(command.currentPassword()).chars().allMatch(value -> value == 0));
        assertTrue(new String(command.newPassword()).chars().allMatch(value -> value == 0));
    }

    @Test
    void onlyPlatformAdministratorCanResetPasswordAndNoTemporaryCredentialIsCreated() {
        UUID accountId = UUID.randomUUID();
        repository.accountSecurity = new IamAdministrationRepository.AccountSecurityContext(
                accountId, "$argon2id$current", ManagedAccount.Status.SUSPENDED, 11);
        var command = new IamAdministrationService.ResetAccountPassword(
                "platform-selected-strong-password".toCharArray(), "Verified recovery request");

        IamAdministrationService.PasswordChange result = service.resetAccountPassword(
                platform(), accountId, command);

        assertEquals(12, result.sessionGeneration());
        assertEquals("PASSWORD_RESET_BY_PLATFORM", repository.lastPasswordEventType);
        assertEquals("Verified recovery request", repository.lastLifecycleReason);
        assertTrue(new String(command.newPassword()).chars().allMatch(value -> value == 0));
        assertThrows(IamAccessDeniedException.class, () -> service.resetAccountPassword(
                operator("/root", "account:manage"), accountId,
                new IamAdministrationService.ResetAccountPassword(
                        "another-strong-password".toCharArray(), "Unauthorized reset")));
    }

    @Test
    void assigningRoleInvalidatesExistingAuthorizationBeforePersistence() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, accountId,
                organizationId, "/root/east", 3);
        repository.role = new IamAdministrationRepository.RoleContext(roleId, organizationId,
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:view"));
        long result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.assignRole(orgAdmin("/root"), membershipId, roleId));
        assertEquals(4, result);
        assertEquals(4, repository.persistedGeneration);
        assertEquals(4, generations.generation);
    }

    @Test
    void revokingRoleImmediatelyInvalidatesAuthorizationAndWritesAudit() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, accountId,
                organizationId, "/root/east", 8);
        repository.role = new IamAdministrationRepository.RoleContext(roleId, organizationId,
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:view"));
        repository.roleAssigned = true;

        long result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.revokeRole(orgAdmin("/root"), membershipId, roleId, "Duty changed"));

        assertEquals(9, result);
        assertEquals(9, repository.persistedGeneration);
        assertEquals(9, generations.generation);
        assertTrue(!repository.roleAssigned);
        assertEquals(1, repository.iamLifecycleAuditCount);
        assertEquals("Duty changed", repository.lastLifecycleReason);
    }

    @Test
    void suspendingMembershipImmediatelyInvalidatesAllTenantAuthorization() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, accountId,
                organizationId, "/root/east", 4);

        long result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.suspendMembership(orgAdmin("/root"), membershipId, "Employment ended"));

        assertEquals(5, result);
        assertEquals(5, repository.persistedGeneration);
        assertEquals(5, generations.generation);
        assertTrue(repository.membershipSuspended);
        assertEquals(1, repository.iamLifecycleAuditCount);
        assertEquals("Employment ended", repository.lastLifecycleReason);
    }

    @Test
    void activatingSuspendedMembershipInvalidatesStaleTenantSessionsAndAudits() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        repository.membershipLifecycle = new IamAdministrationRepository.MembershipLifecycleContext(
                membershipId, accountId, organizationId, "/root/east",
                IamAdministrationRepository.MembershipStatus.SUSPENDED, 6);
        repository.membershipSuspended = true;

        long result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.activateMembership(orgAdmin("/root"), membershipId, "Contract renewed"));

        assertEquals(7, result);
        assertEquals(7, repository.persistedGeneration);
        assertTrue(!repository.membershipSuspended);
        assertEquals(1, repository.iamLifecycleAuditCount);
        assertEquals("Contract renewed", repository.lastLifecycleReason);
    }

    @Test
    void roleAndMembershipQueriesRemainBoundToRequestedOrganizationPath() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(
                organizationId, "/root/east");
        repository.roleViews = List.of(new IamAdministrationRepository.RoleView(roleId, organizationId,
                "operator", "Operator", IamAdministrationRepository.RoleType.OPERATOR,
                "ACTIVE", 1, Set.of("device:view")));
        repository.membershipViews = List.of(new IamAdministrationRepository.MembershipView(
                membershipId, UUID.randomUUID(), organizationId, "/root/east", "user.one",
                Optional.of("user@example.com"), IamAdministrationRepository.MembershipStatus.ACTIVE,
                Set.of(roleId)));

        var eastOperator = operator("/root/east", "role:view", "account:view");
        assertEquals(1, TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.listRoles(eastOperator, organizationId)).size());
        assertEquals(1, TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.listMemberships(eastOperator, organizationId)).size());
        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.listRoles(operator("/root/west", "role:view"), organizationId)));
    }

    @Test
    void tenantRoleCannotContainPlatformOnlyPermission() {
        UUID organizationId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root");
        var command = new IamAdministrationService.CreateRole(organizationId, "unsafe", "Unsafe",
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("platform:all"));

        assertThrows(IllegalArgumentException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.createRole(orgAdmin("/root"), command)));
    }

    @Test
    void roleTypeEnforcesExplicitPermissionShape() {
        UUID organizationId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root");

        assertThrows(IllegalArgumentException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant), () -> service.createRole(
                        orgAdmin("/root"), new IamAdministrationService.CreateRole(organizationId,
                                "admin-with-list", "Admin", IamAdministrationRepository.RoleType.ORGANIZATION_ADMIN,
                                Set.of("device:view")))));
        assertThrows(IllegalArgumentException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant), () -> service.createRole(
                        orgAdmin("/root"), new IamAdministrationService.CreateRole(organizationId,
                                "empty-operator", "Operator", IamAdministrationRepository.RoleType.OPERATOR,
                                Set.of()))));
    }

    @Test
    void replacingRolePermissionsInvalidatesEveryAssignedAccount() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root/east");
        repository.role = new IamAdministrationRepository.RoleContext(roleId, organizationId,
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:view"));
        repository.roleMemberships = List.of(
                new IamAdministrationRepository.MembershipContext(UUID.randomUUID(), UUID.randomUUID(),
                        organizationId, "/root/east", 2),
                new IamAdministrationRepository.MembershipContext(UUID.randomUUID(), UUID.randomUUID(),
                        organizationId, "/root/east", 7));

        IamAdministrationService.RoleAuthorizationChange result = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.replaceRolePermissions(orgAdmin("/root"),
                        roleId, Set.of("device:operate"), "Operations duty changed"));

        assertEquals(2, result.affectedAccountCount());
        assertEquals(Set.of("device:operate"), repository.persistedRolePermissions);
        assertEquals(List.of(3L, 8L), repository.authorizationChanges.stream()
                .map(IamAdministrationRepository.AccountAuthorizationChange::authorizationGeneration).toList());
        assertEquals(1, repository.iamLifecycleAuditCount);
        assertEquals("Operations duty changed", repository.lastLifecycleReason);
    }

    @Test
    void retiringRoleInvalidatesAssignedAccountsAndAuditsReason() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root");
        repository.role = new IamAdministrationRepository.RoleContext(roleId, organizationId,
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:view"));
        repository.roleMemberships = List.of(new IamAdministrationRepository.MembershipContext(
                UUID.randomUUID(), UUID.randomUUID(), organizationId, "/root", 11));

        IamAdministrationService.RoleAuthorizationChange result = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.retireRole(
                        orgAdmin("/root"), roleId, "Role replaced"));

        assertEquals(1, result.affectedAccountCount());
        assertTrue(repository.roleRetired);
        assertEquals(12, repository.authorizationChanges.getFirst().authorizationGeneration());
        assertEquals("Role replaced", repository.lastLifecycleReason);
    }

    @Test
    void operatorCannotDelegatePermissionItDoesNotHold() {
        UUID organizationId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root");
        var command = new IamAdministrationService.CreateRole(organizationId, "operator", "Operator",
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:operate"));

        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.createRole(operator("/root", "role:manage", "device:view"), command)));
    }

    @Test
    void operatorCannotCreateOrAssignOrganizationAdministratorRole() {
        UUID organizationId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root");
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, accountId,
                organizationId, "/root", 0);
        repository.role = new IamAdministrationRepository.RoleContext(roleId, organizationId,
                IamAdministrationRepository.RoleType.ORGANIZATION_ADMIN, Set.of());
        AuthenticatedPrincipal operator = operator("/root", "role:manage", "account:manage");
        var command = new IamAdministrationService.CreateRole(organizationId, "admin", "Admin",
                IamAdministrationRepository.RoleType.ORGANIZATION_ADMIN, Set.of());

        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.createRole(operator, command)));
        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.assignRole(operator, membershipId, roleId)));
    }

    @Test
    void operatorCannotAssignOperatorRoleWithBroaderPermissions() {
        UUID organizationId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, UUID.randomUUID(),
                organizationId, "/root", 0);
        repository.role = new IamAdministrationRepository.RoleContext(roleId, organizationId,
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:operate"));

        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.assignRole(operator("/root", "account:manage", "device:view"),
                                membershipId, roleId)));
    }

    @Test
    void permissionFromAnotherMembershipCannotBeDelegatedIntoTargetOrganization() {
        UUID organizationId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(organizationId, "/root/east");
        AuthenticatedPrincipal principal = scopedPrincipal(
                AuthenticatedPrincipal.PrincipalType.OPERATOR,
                new OrganizationAccessScope("/root/east", OrganizationAccessScope.AccessLevel.OPERATOR,
                        Set.of("role:manage")),
                new OrganizationAccessScope("/root/west", OrganizationAccessScope.AccessLevel.OPERATOR,
                        Set.of("device:operate")));
        var command = new IamAdministrationService.CreateRole(organizationId, "east-operator", "East Operator",
                IamAdministrationRepository.RoleType.OPERATOR, Set.of("device:operate"));

        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.createRole(principal, command)));
    }

    @Test
    void administratorScopeInOneSubtreeDoesNotElevateOperatorScopeElsewhere() {
        UUID westOrganizationId = UUID.randomUUID();
        repository.organization = new IamAdministrationRepository.OrganizationContext(
                westOrganizationId, "/root/west");
        repository.membership = new IamAdministrationRepository.MembershipContext(UUID.randomUUID(),
                UUID.randomUUID(), westOrganizationId, "/root/west", 0);
        AuthenticatedPrincipal principal = scopedPrincipal(
                AuthenticatedPrincipal.PrincipalType.ORGANIZATION_ADMIN,
                new OrganizationAccessScope("/root/east", OrganizationAccessScope.AccessLevel.ADMINISTRATOR,
                        Set.of()),
                new OrganizationAccessScope("/root/west", OrganizationAccessScope.AccessLevel.OPERATOR,
                        Set.of("role:manage", "account:manage")));
        var command = new IamAdministrationService.CreateRole(westOrganizationId, "west-admin", "West Admin",
                IamAdministrationRepository.RoleType.ORGANIZATION_ADMIN, Set.of());

        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.createRole(principal, command)));
        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.listResourceGrants(principal, repository.membership.membershipId())));
    }

    @Test
    void permissionCatalogRequiresReadPermissionForOperator() {
        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.listPermissionCatalog(operator("/root", "menu:view"))));
    }

    @Test
    void menuListContainsOnlyEntriesAuthorizedForOperator() throws Exception {
        repository.menus = List.of(
                menu("devices", "device:view", 10),
                menu("alarms", "alarm:view", 20),
                menu("help", null, 30));

        List<NavigationMenu> visible = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.listVisibleMenus(operator("/root", "menu:view", "device:view")));

        assertEquals(List.of("devices", "help"), visible.stream().map(NavigationMenu::code).toList());
    }

    @Test
    void permissionCatalogReturnsTenantAssignableVocabulary() throws Exception {
        repository.permissions = List.of(new PermissionDefinition(
                "device:view", "device", "VIEW", "View devices"));

        List<PermissionDefinition> result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.listPermissionCatalog(operator("/root", "permission:view")));

        assertEquals(List.of("device:view"), result.stream().map(PermissionDefinition::code).toList());
    }

    @Test
    void organizationAdministratorGrantsDeviceAccessAndInvalidatesAuthorization() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, accountId,
                organizationId, "/root/east", 5);
        repository.grantTarget = new IamAdministrationRepository.GrantTargetContext(
                organizationId, "/root/east/site");

        IamAdministrationService.ResourceGrantChange result = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.grantResourceAccess(orgAdmin("/root"),
                        membershipId, new IamAdministrationService.GrantResourceAccess(
                                ManagedResourceGrant.ResourceType.DEVICE, deviceId, ResourceAction.OPERATE)));

        assertEquals(6, result.authorizationGeneration());
        assertEquals(ResourceAction.OPERATE, result.grant().action());
        assertEquals(6, repository.persistedGeneration);
        assertEquals(1, repository.grantAuditCount);
    }

    @Test
    void administratorCannotGrantResourceOutsideOrganizationScope() {
        UUID organizationId = UUID.randomUUID();
        repository.membership = new IamAdministrationRepository.MembershipContext(UUID.randomUUID(),
                UUID.randomUUID(), organizationId, "/root/east", 0);
        repository.grantTarget = new IamAdministrationRepository.GrantTargetContext(
                UUID.randomUUID(), "/root/west");

        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.grantResourceAccess(orgAdmin("/root/east"),
                                repository.membership.membershipId(),
                                new IamAdministrationService.GrantResourceAccess(
                                        ManagedResourceGrant.ResourceType.DEVICE, UUID.randomUUID(),
                                        ResourceAction.VIEW))));
    }

    @Test
    void operatorCannotManageExplicitResourceGrants() {
        assertThrows(IamAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.listResourceGrants(operator("/root", "account:manage"),
                                UUID.randomUUID())));
    }

    @Test
    void revokingGrantInvalidatesAuthorizationAndRecordsReason() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        ManagedResourceGrant grant = ManagedResourceGrant.active(UUID.randomUUID(), tenant.value(),
                membershipId, ManagedResourceGrant.ResourceType.DEVICE, UUID.randomUUID(),
                ResourceAction.VIEW, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId, accountId,
                organizationId, "/root/east", 2);
        repository.grants.put(grant.id(), grant);

        IamAdministrationService.ResourceGrantChange result = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.revokeResourceAccess(
                        orgAdmin("/root"), grant.id(), "Device reassigned"));

        assertEquals(3, result.authorizationGeneration());
        assertTrue(repository.grants.isEmpty());
        assertEquals("Device reassigned", repository.lastRevokeReason);
        assertEquals(1, repository.grantAuditCount);
    }

    @Test
    void duplicateResourceGrantIsIdempotentAndDoesNotAdvanceGeneration() throws Exception {
        UUID membershipId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        ManagedResourceGrant existing = ManagedResourceGrant.active(UUID.randomUUID(), tenant.value(),
                membershipId, ManagedResourceGrant.ResourceType.DEVICE, deviceId, ResourceAction.VIEW,
                UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        repository.membership = new IamAdministrationRepository.MembershipContext(membershipId,
                UUID.randomUUID(), organizationId, "/root", 7);
        repository.grantTarget = new IamAdministrationRepository.GrantTargetContext(organizationId, "/root");
        repository.grants.put(existing.id(), existing);

        IamAdministrationService.ResourceGrantChange result = TenantContext.call(
                new TenantScope.Scoped(tenant), () -> service.grantResourceAccess(orgAdmin("/root"),
                        membershipId, new IamAdministrationService.GrantResourceAccess(
                                ManagedResourceGrant.ResourceType.DEVICE, deviceId, ResourceAction.VIEW)));

        assertEquals(existing.id(), result.grant().id());
        assertEquals(7, result.authorizationGeneration());
        assertEquals(0, repository.grantAuditCount);
        assertEquals(0, generations.generation);
    }

    @Test
    void rejectsActionUnsupportedByResourceType() {
        assertThrows(IllegalArgumentException.class,
                () -> new IamAdministrationService.GrantResourceAccess(
                        ManagedResourceGrant.ResourceType.FACILITY, UUID.randomUUID(),
                        ResourceAction.OPERATE));
    }

    private AuthenticatedPrincipal platform() {
        return platform(UUID.randomUUID());
    }
    private AuthenticatedPrincipal platform(UUID accountId) {
        return new AuthenticatedPrincipal(accountId, UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN, Optional.empty(), Set.of("platform:all"),
                Set.of(), Set.of());
    }
    private AuthenticatedPrincipal orgAdmin(String path) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.ORGANIZATION_ADMIN, Optional.of(tenant), Set.of(),
                Set.of(path), Set.of());
    }
    private AuthenticatedPrincipal operator(String path, String... permissions) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permissions),
                Set.of(path), Set.of());
    }
    private AuthenticatedPrincipal scopedPrincipal(AuthenticatedPrincipal.PrincipalType type,
                                                    OrganizationAccessScope... scopes) {
        Set<OrganizationAccessScope> organizationScopes = Set.of(scopes);
        Set<String> permissions = organizationScopes.stream()
                .flatMap(scope -> scope.permissions().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0, type,
                Optional.of(tenant), permissions, Set.of(), organizationScopes, Set.of());
    }

    private NavigationMenu menu(String code, String permission, int sortOrder) {
        return new NavigationMenu(UUID.randomUUID(), Optional.empty(), code,
                Optional.of("/" + code), Optional.ofNullable(permission), sortOrder);
    }

    private static final class MemoryGenerationRegistry implements AuthorizationGenerationRegistry {
        private long generation;
        public long current(UUID accountId, Optional<UUID> tenantId, long baseline) {
            generation = Math.max(generation, baseline); return generation;
        }
        public long revoke(UUID accountId, Optional<UUID> tenantId, long baseline) {
            generation = Math.max(generation, baseline) + 1; return generation;
        }
    }

    private static final class MemoryAccountGenerationRegistry implements AccountSessionGenerationRegistry {
        private long generation;
        public long current(UUID accountId, long baseline) {
            generation = Math.max(generation, baseline); return generation;
        }
        public long revokeAll(UUID accountId, long baseline) {
            generation = Math.max(generation, baseline) + 1; return generation;
        }
    }

    private static final class MemoryRepository implements IamAdministrationRepository {
        private static final Set<String> PLATFORM_ONLY = Set.of(
                "platform:all", "account:create", "menu:manage", "ca:manufacture");
        private OrganizationContext organization;
        private AccountSecurityContext accountSecurity;
        private MembershipContext membership;
        private MembershipLifecycleContext membershipLifecycle;
        private RoleContext role;
        private List<RoleView> roleViews = List.of();
        private List<MembershipView> membershipViews = List.of();
        private List<PermissionDefinition> permissions = List.of();
        private List<NavigationMenu> menus = List.of();
        private GrantTargetContext grantTarget;
        private final Map<UUID, ManagedResourceGrant> grants = new HashMap<>();
        private long persistedGeneration;
        private int grantAuditCount;
        private String lastRevokeReason;
        private boolean roleAssigned;
        private boolean membershipSuspended;
        private int iamLifecycleAuditCount;
        private String lastLifecycleReason;
        private List<MembershipContext> roleMemberships = List.of();
        private Set<String> persistedRolePermissions = Set.of();
        private List<AccountAuthorizationChange> authorizationChanges = List.of();
        private boolean roleRetired;
        private long persistedAccountSessionGeneration;
        private String persistedPasswordHash;
        private String lastPasswordEventType;
        public boolean accountIdentityExists(String login, String email) { return false; }
        public void insertAccount(ManagedAccount account) { }
        public Optional<AccountSecurityContext> findAccountSecurity(UUID accountId) {
            return Optional.ofNullable(accountSecurity);
        }
        public void changePassword(AccountSecurityContext account, String passwordHash,
                                   long sessionGeneration, UUID actorAccountId, UUID sessionId,
                                   String eventType, String reason, Instant now) {
            persistedPasswordHash = passwordHash;
            persistedAccountSessionGeneration = sessionGeneration;
            lastPasswordEventType = eventType;
            lastLifecycleReason = reason;
            accountSecurity = new AccountSecurityContext(account.accountId(), passwordHash,
                    account.status(), sessionGeneration);
        }
        public void changeAccountStatus(AccountSecurityContext account, ManagedAccount.Status status,
                                        long sessionGeneration, UUID actorAccountId, UUID sessionId,
                                        String reason, Instant now) {
            accountSecurity = new AccountSecurityContext(account.accountId(), account.passwordHash(),
                    status, sessionGeneration);
            persistedAccountSessionGeneration = sessionGeneration;
            lastLifecycleReason = reason;
        }
        public Optional<OrganizationContext> findOrganization(UUID id) { return Optional.ofNullable(organization); }
        public boolean membershipExists(UUID accountId, UUID organizationId) { return false; }
        public UUID insertMembership(UUID tenantId, UUID accountId, UUID organizationId, Instant now) { return UUID.randomUUID(); }
        public boolean roleCodeExists(String code) { return false; }
        public Set<String> findTenantAssignablePermissions(Set<String> codes) {
            return codes.stream().filter(code -> !PLATFORM_ONLY.contains(code))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        public List<PermissionDefinition> listTenantAssignablePermissions() { return permissions; }
        public List<NavigationMenu> listActiveMenus() { return menus; }
        public UUID insertRole(UUID tenantId, UUID organizationId, String code, String displayName,
                               RoleType roleType, Set<String> permissions, Instant now) { return UUID.randomUUID(); }
        public Optional<MembershipContext> findMembership(UUID id) { return Optional.ofNullable(membership); }
        public Optional<MembershipLifecycleContext> findMembershipLifecycle(UUID id) {
            return Optional.ofNullable(membershipLifecycle);
        }
        public Optional<RoleContext> findRole(UUID id) { return Optional.ofNullable(role); }
        public List<MembershipContext> findActiveRoleMemberships(UUID roleId) { return roleMemberships; }
        public List<RoleView> listRoles(UUID organizationId) { return roleViews; }
        public List<MembershipView> listMemberships(UUID organizationId) { return membershipViews; }
        public boolean assignmentExists(UUID membershipId, UUID roleId) { return roleAssigned; }
        public void assignRole(UUID tenantId, UUID membershipId, UUID roleId, UUID accountId,
                               long generation, Instant now) {
            roleAssigned = true; persistedGeneration = generation;
        }
        public void revokeRole(UUID tenantId, UUID membershipId, UUID roleId,
                               UUID actorAccountId, UUID sessionId, UUID accountId,
                               long generation, String reason, Instant now) {
            roleAssigned = false; persistedGeneration = generation;
            lastLifecycleReason = reason; iamLifecycleAuditCount++;
        }
        public void suspendMembership(UUID tenantId, MembershipContext membership,
                                      UUID actorAccountId, UUID sessionId,
                                      long generation, String reason, Instant now) {
            membershipSuspended = true; persistedGeneration = generation;
            lastLifecycleReason = reason; iamLifecycleAuditCount++;
        }
        public void activateMembership(UUID tenantId, MembershipLifecycleContext membership,
                                       UUID actorAccountId, UUID sessionId,
                                       long generation, String reason, Instant now) {
            membershipSuspended = false; persistedGeneration = generation;
            lastLifecycleReason = reason; iamLifecycleAuditCount++;
        }
        public void replaceRolePermissions(UUID tenantId, RoleContext role, Set<String> permissionCodes,
                                           List<AccountAuthorizationChange> changes,
                                           UUID actorAccountId, UUID sessionId, String reason, Instant now) {
            persistedRolePermissions = Set.copyOf(permissionCodes);
            authorizationChanges = List.copyOf(changes);
            lastLifecycleReason = reason; iamLifecycleAuditCount++;
        }
        public void retireRole(UUID tenantId, RoleContext role,
                               List<AccountAuthorizationChange> changes,
                               UUID actorAccountId, UUID sessionId, String reason, Instant now) {
            roleRetired = true; authorizationChanges = List.copyOf(changes);
            lastLifecycleReason = reason; iamLifecycleAuditCount++;
        }
        public Optional<GrantTargetContext> findGrantTarget(ManagedResourceGrant.ResourceType type,
                                                            UUID resourceId) {
            return Optional.ofNullable(grantTarget);
        }
        public Optional<ManagedResourceGrant> findActiveResourceGrant(UUID grantId) {
            return Optional.ofNullable(grants.get(grantId));
        }
        public Optional<ManagedResourceGrant> findActiveResourceGrant(UUID membershipId,
                ManagedResourceGrant.ResourceType resourceType, UUID resourceId, ResourceAction action) {
            return grants.values().stream().filter(grant -> grant.membershipId().equals(membershipId)
                    && grant.resourceType() == resourceType && grant.resourceId().equals(resourceId)
                    && grant.action() == action).findFirst();
        }
        public List<ManagedResourceGrant> listActiveResourceGrants(UUID membershipId) {
            return grants.values().stream().filter(grant -> grant.membershipId().equals(membershipId)).toList();
        }
        public ManagedResourceGrant upsertResourceGrant(UUID tenantId, UUID membershipId,
                ManagedResourceGrant.ResourceType resourceType, UUID resourceId, ResourceAction action,
                UUID actorAccountId, UUID sessionId, UUID accountId, long generation, Instant now) {
            ManagedResourceGrant grant = ManagedResourceGrant.active(UUID.randomUUID(), tenantId, membershipId,
                    resourceType, resourceId, action, actorAccountId, now);
            grants.put(grant.id(), grant); persistedGeneration = generation; grantAuditCount++; return grant;
        }
        public void revokeResourceGrant(ManagedResourceGrant grant, UUID actorAccountId, UUID sessionId,
                UUID accountId, long generation, String reason, Instant now) {
            grants.remove(grant.id()); persistedGeneration = generation; lastRevokeReason = reason;
            grantAuditCount++;
        }
    }
}
