package com.accuenergy.octopus.mgmt.application.iam;

import com.accuenergy.octopus.mgmt.domain.identity.ManagedAccount;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedResourceGrant;
import com.accuenergy.octopus.mgmt.domain.identity.NavigationMenu;
import com.accuenergy.octopus.mgmt.domain.identity.PermissionDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IamAdministrationRepository {
    boolean accountIdentityExists(String loginName, String email);
    void insertAccount(ManagedAccount account);
    Optional<AccountSecurityContext> findAccountSecurity(UUID accountId);
    void changePassword(AccountSecurityContext account, String passwordHash, long sessionGeneration,
                        UUID actorAccountId, UUID sessionId, String eventType,
                        String reason, Instant now);
    void changeAccountStatus(AccountSecurityContext account, ManagedAccount.Status status,
                             long sessionGeneration, UUID actorAccountId, UUID sessionId,
                             String reason, Instant now);
    Optional<OrganizationContext> findOrganization(UUID organizationId);
    boolean membershipExists(UUID accountId, UUID organizationId);
    UUID insertMembership(UUID tenantId, UUID accountId, UUID organizationId, Instant now);
    boolean roleCodeExists(String code);
    Set<String> findTenantAssignablePermissions(Set<String> codes);
    List<PermissionDefinition> listTenantAssignablePermissions();
    List<NavigationMenu> listActiveMenus();
    UUID insertRole(UUID tenantId, UUID organizationId, String code, String displayName,
                    RoleType roleType, Set<String> permissionCodes, Instant now);
    Optional<MembershipContext> findMembership(UUID membershipId);
    Optional<MembershipLifecycleContext> findMembershipLifecycle(UUID membershipId);
    Optional<RoleContext> findRole(UUID roleId);
    List<MembershipContext> findActiveRoleMemberships(UUID roleId);
    List<RoleView> listRoles(UUID organizationId);
    List<MembershipView> listMemberships(UUID organizationId);
    boolean assignmentExists(UUID membershipId, UUID roleId);
    void assignRole(UUID tenantId, UUID membershipId, UUID roleId,
                    UUID accountId, long authorizationGeneration, Instant now);
    void revokeRole(UUID tenantId, UUID membershipId, UUID roleId,
                    UUID actorAccountId, UUID sessionId, UUID accountId,
                    long authorizationGeneration, String reason, Instant now);
    void suspendMembership(UUID tenantId, MembershipContext membership,
                           UUID actorAccountId, UUID sessionId,
                           long authorizationGeneration, String reason, Instant now);
    void activateMembership(UUID tenantId, MembershipLifecycleContext membership,
                            UUID actorAccountId, UUID sessionId,
                            long authorizationGeneration, String reason, Instant now);
    void replaceRolePermissions(UUID tenantId, RoleContext role, Set<String> permissionCodes,
                                List<AccountAuthorizationChange> authorizationChanges,
                                UUID actorAccountId, UUID sessionId, String reason, Instant now);
    void retireRole(UUID tenantId, RoleContext role,
                    List<AccountAuthorizationChange> authorizationChanges,
                    UUID actorAccountId, UUID sessionId, String reason, Instant now);
    Optional<GrantTargetContext> findGrantTarget(ManagedResourceGrant.ResourceType resourceType,
                                                 UUID resourceId);
    Optional<ManagedResourceGrant> findActiveResourceGrant(UUID grantId);
    Optional<ManagedResourceGrant> findActiveResourceGrant(UUID membershipId,
            ManagedResourceGrant.ResourceType resourceType, UUID resourceId,
            com.accuenergy.octopus.common.security.ResourceAction action);
    List<ManagedResourceGrant> listActiveResourceGrants(UUID membershipId);
    ManagedResourceGrant upsertResourceGrant(UUID tenantId, UUID membershipId,
            ManagedResourceGrant.ResourceType resourceType, UUID resourceId,
            com.accuenergy.octopus.common.security.ResourceAction action,
            UUID actorAccountId, UUID sessionId, UUID accountId,
            long authorizationGeneration, Instant now);
    void revokeResourceGrant(ManagedResourceGrant grant, UUID actorAccountId, UUID sessionId,
                             UUID accountId, long authorizationGeneration,
                             String reason, Instant now);

    record OrganizationContext(UUID organizationId, String path) { }
    record AccountSecurityContext(UUID accountId, String passwordHash,
                                  ManagedAccount.Status status, long sessionGeneration) { }
    record MembershipContext(UUID membershipId, UUID accountId, UUID organizationId,
                             String organizationPath, long authorizationGeneration) { }
    record MembershipLifecycleContext(UUID membershipId, UUID accountId, UUID organizationId,
                                      String organizationPath, MembershipStatus status,
                                      long authorizationGeneration) { }
    record RoleContext(UUID roleId, UUID organizationId, RoleType roleType,
                       Set<String> permissionCodes) {
        public RoleContext { permissionCodes = Set.copyOf(permissionCodes); }
    }
    record GrantTargetContext(UUID organizationId, String organizationPath) { }
    record RoleView(UUID roleId, UUID organizationId, String code, String displayName,
                    RoleType roleType, String status, long version, Set<String> permissionCodes) {
        public RoleView { permissionCodes = Set.copyOf(permissionCodes); }
    }
    record MembershipView(UUID membershipId, UUID accountId, UUID organizationId,
                          String organizationPath, String loginName, Optional<String> email,
                          MembershipStatus status, Set<UUID> roleIds) {
        public MembershipView {
            email = java.util.Objects.requireNonNull(email, "email");
            roleIds = Set.copyOf(roleIds);
        }
    }
    record AccountAuthorizationChange(UUID accountId, long authorizationGeneration) {
        public AccountAuthorizationChange {
            if (authorizationGeneration < 0) {
                throw new IllegalArgumentException("authorizationGeneration must be non-negative");
            }
        }
    }
    enum RoleType { ORGANIZATION_ADMIN, OPERATOR }
    enum MembershipStatus { ACTIVE, SUSPENDED }
}
