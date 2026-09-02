package com.accuenergy.octopus.mgmt.infrastructure.persistence.iam;

import com.accuenergy.octopus.mgmt.application.iam.IamAdministrationRepository;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedAccount;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedResourceGrant;
import com.accuenergy.octopus.mgmt.domain.identity.NavigationMenu;
import com.accuenergy.octopus.mgmt.domain.identity.PermissionDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisIamAdministrationRepository implements IamAdministrationRepository {
    private final IamAdministrationMapper mapper;
    public MybatisIamAdministrationRepository(IamAdministrationMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public boolean accountIdentityExists(String loginName, String email) {
        return mapper.countAccountIdentity(loginName, email) > 0;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void insertAccount(ManagedAccount account) {
        var row = new IamAdministrationMapper.AccountRow(account.id(), account.loginName(),
                account.email().orElse(null), account.phoneE164().orElse(null), account.passwordHash(),
                account.status().name(), account.passwordChangedAt(), account.createdAt(), account.updatedAt());
        if (mapper.insertAccount(row) != 1) throw new IllegalStateException("Unable to insert account");
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<AccountSecurityContext> findAccountSecurity(UUID accountId) {
        return Optional.ofNullable(mapper.findAccountSecurity(accountId)).map(row ->
                new AccountSecurityContext(row.accountId(), row.passwordHash(),
                        ManagedAccount.Status.valueOf(row.status()),
                        row.sessionGeneration()));
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void changePassword(AccountSecurityContext account, String passwordHash, long sessionGeneration,
                               UUID actorAccountId, UUID sessionId, String eventType,
                               String reason, Instant now) {
        if (mapper.updatePassword(account.accountId(), passwordHash, sessionGeneration, now) != 1) {
            throw new IllegalStateException("Account was concurrently removed");
        }
        if (mapper.insertPasswordAudit(UUID.randomUUID(), actorAccountId, sessionId, account.accountId(),
                eventType, sessionGeneration, reason, now) != 1) {
            throw new IllegalStateException("Unable to audit password change");
        }
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void changeAccountStatus(AccountSecurityContext account, ManagedAccount.Status status,
                                    long sessionGeneration, UUID actorAccountId, UUID sessionId,
                                    String reason, Instant now) {
        if (mapper.updateAccountStatus(account.accountId(), status.name(), sessionGeneration, now) != 1) {
            throw new IllegalStateException("Account was concurrently removed");
        }
        if (mapper.insertAccountStatusAudit(UUID.randomUUID(), actorAccountId, sessionId,
                account.accountId(), account.status().name(), status.name(), sessionGeneration, reason, now) != 1) {
            throw new IllegalStateException("Unable to audit account status change");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<OrganizationContext> findOrganization(UUID organizationId) {
        return Optional.ofNullable(mapper.findOrganization(organizationId))
                .map(row -> new OrganizationContext(row.organizationId(), row.path()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean membershipExists(UUID accountId, UUID organizationId) {
        return mapper.countMembership(accountId, organizationId) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public UUID insertMembership(UUID tenantId, UUID accountId, UUID organizationId, Instant now) {
        UUID id = UUID.randomUUID();
        if (mapper.insertMembership(id, tenantId, accountId, organizationId, now) != 1) {
            throw new IllegalArgumentException("Unknown or inactive account");
        }
        mapper.initializeTenantSecurity(tenantId, accountId, now);
        return id;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean roleCodeExists(String code) { return mapper.countRoleCode(code) > 0; }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Set<String> findTenantAssignablePermissions(Set<String> codes) {
        if (codes.isEmpty()) return Set.of();
        return Set.copyOf(mapper.findTenantAssignablePermissions(codes));
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public List<PermissionDefinition> listTenantAssignablePermissions() {
        return mapper.listTenantAssignablePermissions().stream()
                .map(row -> new PermissionDefinition(row.code(), row.resourceType(), row.action(),
                        row.description()))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public List<NavigationMenu> listActiveMenus() {
        return mapper.listActiveMenus().stream()
                .map(row -> new NavigationMenu(row.id(), Optional.ofNullable(row.parentId()), row.code(),
                        Optional.ofNullable(row.route()), Optional.ofNullable(row.requiredPermissionCode()),
                        row.sortOrder()))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public UUID insertRole(UUID tenantId, UUID organizationId, String code, String displayName,
                           RoleType roleType, Set<String> permissionCodes, Instant now) {
        UUID id = UUID.randomUUID();
        if (mapper.insertRole(id, tenantId, organizationId, code, displayName, roleType.name(), now) != 1) {
            throw new IllegalStateException("Unable to insert role");
        }
        for (String permission : permissionCodes) {
            if (mapper.insertRolePermission(tenantId, id, permission, now) != 1) {
                throw new IllegalStateException("Unable to assign role permission " + permission);
            }
        }
        return id;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<MembershipContext> findMembership(UUID membershipId) {
        return Optional.ofNullable(mapper.findMembership(membershipId)).map(row -> new MembershipContext(
                row.membershipId(), row.accountId(), row.organizationId(), row.organizationPath(),
                row.authorizationGeneration()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<MembershipLifecycleContext> findMembershipLifecycle(UUID membershipId) {
        return Optional.ofNullable(mapper.findMembershipLifecycle(membershipId)).map(row ->
                new MembershipLifecycleContext(row.membershipId(), row.accountId(), row.organizationId(),
                        row.organizationPath(), MembershipStatus.valueOf(row.status()),
                        row.authorizationGeneration()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<RoleContext> findRole(UUID roleId) {
        return Optional.ofNullable(mapper.findRole(roleId)).map(row -> new RoleContext(
                row.roleId(), row.organizationId(), RoleType.valueOf(row.roleType()),
                Set.copyOf(mapper.findRolePermissions(roleId))));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<MembershipContext> findActiveRoleMemberships(UUID roleId) {
        return mapper.findActiveRoleMemberships(roleId).stream().map(row -> new MembershipContext(
                row.membershipId(), row.accountId(), row.organizationId(), row.organizationPath(),
                row.authorizationGeneration())).toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<RoleView> listRoles(UUID organizationId) {
        return mapper.listRoles(organizationId).stream().map(row -> new RoleView(row.roleId(),
                row.organizationId(), row.code(), row.displayName(), RoleType.valueOf(row.roleType()),
                row.status(), row.version(), parseStrings(row.permissionCodes()))).toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<MembershipView> listMemberships(UUID organizationId) {
        return mapper.listMemberships(organizationId).stream().map(row -> new MembershipView(
                row.membershipId(), row.accountId(), row.organizationId(), row.organizationPath(),
                row.loginName(), Optional.ofNullable(row.email()), MembershipStatus.valueOf(row.status()),
                parseUuids(row.roleIds()))).toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean assignmentExists(UUID membershipId, UUID roleId) {
        return mapper.countAssignment(membershipId, roleId) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void assignRole(UUID tenantId, UUID membershipId, UUID roleId,
                           UUID accountId, long authorizationGeneration, Instant now) {
        if (mapper.insertAssignment(tenantId, membershipId, roleId, now) != 1
                || mapper.advanceAuthorizationGeneration(tenantId, accountId,
                        authorizationGeneration, now) != 1) {
            throw new IllegalStateException("Unable to assign membership role");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void revokeRole(UUID tenantId, UUID membershipId, UUID roleId,
                           UUID actorAccountId, UUID sessionId, UUID accountId,
                           long authorizationGeneration, String reason, Instant now) {
        if (mapper.deleteAssignment(tenantId, membershipId, roleId) != 1) {
            throw new IllegalStateException("Membership role was concurrently revoked or missing");
        }
        if (mapper.advanceAuthorizationGeneration(tenantId, accountId, authorizationGeneration, now) != 1) {
            throw new IllegalStateException("Unable to advance authorization generation");
        }
        if (mapper.insertMembershipRoleAudit(UUID.randomUUID(), tenantId, actorAccountId, sessionId,
                membershipId, roleId, accountId, reason, now) != 1) {
            throw new IllegalStateException("Unable to audit membership role revocation");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void suspendMembership(UUID tenantId, MembershipContext membership,
                                  UUID actorAccountId, UUID sessionId,
                                  long authorizationGeneration, String reason, Instant now) {
        if (mapper.suspendMembership(tenantId, membership.membershipId(), now) != 1) {
            throw new IllegalStateException("Membership was concurrently suspended or missing");
        }
        if (mapper.advanceAuthorizationGeneration(tenantId, membership.accountId(),
                authorizationGeneration, now) != 1) {
            throw new IllegalStateException("Unable to advance authorization generation");
        }
        if (mapper.insertMembershipAudit(UUID.randomUUID(), tenantId, actorAccountId, sessionId,
                "MEMBERSHIP_SUSPENDED",
                membership.membershipId(), membership.accountId(), membership.organizationId(), reason, now) != 1) {
            throw new IllegalStateException("Unable to audit membership suspension");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void activateMembership(UUID tenantId, MembershipLifecycleContext membership,
                                   UUID actorAccountId, UUID sessionId,
                                   long authorizationGeneration, String reason, Instant now) {
        if (mapper.activateMembership(tenantId, membership.membershipId(), now) != 1) {
            throw new IllegalStateException("Membership was concurrently activated or missing");
        }
        if (mapper.advanceAuthorizationGeneration(tenantId, membership.accountId(),
                authorizationGeneration, now) != 1) {
            throw new IllegalStateException("Unable to advance authorization generation");
        }
        if (mapper.insertMembershipAudit(UUID.randomUUID(), tenantId, actorAccountId, sessionId,
                "MEMBERSHIP_ACTIVATED", membership.membershipId(), membership.accountId(),
                membership.organizationId(), reason, now) != 1) {
            throw new IllegalStateException("Unable to audit membership activation");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void replaceRolePermissions(UUID tenantId, RoleContext role, Set<String> permissionCodes,
                                       List<AccountAuthorizationChange> authorizationChanges,
                                       UUID actorAccountId, UUID sessionId, String reason, Instant now) {
        mapper.deleteRolePermissions(tenantId, role.roleId());
        for (String permissionCode : permissionCodes) {
            if (mapper.insertRolePermission(tenantId, role.roleId(), permissionCode, now) != 1) {
                throw new IllegalStateException("Unable to assign role permission " + permissionCode);
            }
        }
        if (mapper.touchRole(tenantId, role.roleId(), now) != 1) {
            throw new IllegalStateException("Role was concurrently changed or retired");
        }
        persistAuthorizationChanges(tenantId, authorizationChanges, now);
        String serializedPermissions = permissionCodes.stream().sorted()
                .collect(java.util.stream.Collectors.joining(","));
        if (mapper.insertRoleAudit(UUID.randomUUID(), tenantId, actorAccountId, sessionId,
                "ROLE_PERMISSIONS_REPLACED", role.roleId(), authorizationChanges.size(),
                serializedPermissions, reason, now) != 1) {
            throw new IllegalStateException("Unable to audit role permission replacement");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void retireRole(UUID tenantId, RoleContext role,
                           List<AccountAuthorizationChange> authorizationChanges,
                           UUID actorAccountId, UUID sessionId, String reason, Instant now) {
        if (mapper.retireRole(tenantId, role.roleId(), now) != 1) {
            throw new IllegalStateException("Role was concurrently changed or retired");
        }
        persistAuthorizationChanges(tenantId, authorizationChanges, now);
        if (mapper.insertRoleAudit(UUID.randomUUID(), tenantId, actorAccountId, sessionId,
                "ROLE_RETIRED", role.roleId(), authorizationChanges.size(), "", reason, now) != 1) {
            throw new IllegalStateException("Unable to audit role retirement");
        }
    }

    private void persistAuthorizationChanges(UUID tenantId,
            List<AccountAuthorizationChange> authorizationChanges, Instant now) {
        for (AccountAuthorizationChange change : authorizationChanges) {
            if (mapper.advanceAuthorizationGeneration(tenantId, change.accountId(),
                    change.authorizationGeneration(), now) != 1) {
                throw new IllegalStateException("Unable to advance authorization generation");
            }
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<GrantTargetContext> findGrantTarget(ManagedResourceGrant.ResourceType resourceType,
                                                        UUID resourceId) {
        IamAdministrationMapper.GrantTargetRow row = switch (resourceType) {
            case DEVICE -> mapper.findDeviceGrantTarget(resourceId);
            case FACILITY -> mapper.findFacilityGrantTarget(resourceId);
            case METER -> mapper.findMeterGrantTarget(resourceId);
        };
        return Optional.ofNullable(row)
                .map(value -> new GrantTargetContext(value.organizationId(), value.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<ManagedResourceGrant> findActiveResourceGrant(UUID grantId) {
        return Optional.ofNullable(mapper.findActiveResourceGrant(grantId))
                .map(MybatisIamAdministrationRepository::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<ManagedResourceGrant> findActiveResourceGrant(UUID membershipId,
            ManagedResourceGrant.ResourceType resourceType, UUID resourceId,
            com.accuenergy.octopus.common.security.ResourceAction action) {
        return Optional.ofNullable(mapper.findActiveResourceGrantByKey(membershipId,
                        resourceType.code(), resourceId, action.name()))
                .map(MybatisIamAdministrationRepository::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<ManagedResourceGrant> listActiveResourceGrants(UUID membershipId) {
        return mapper.listActiveResourceGrants(membershipId).stream()
                .map(MybatisIamAdministrationRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ManagedResourceGrant upsertResourceGrant(UUID tenantId, UUID membershipId,
            ManagedResourceGrant.ResourceType resourceType, UUID resourceId,
            com.accuenergy.octopus.common.security.ResourceAction action,
            UUID actorAccountId, UUID sessionId, UUID accountId,
            long authorizationGeneration, Instant now) {
        UUID requestedId = UUID.randomUUID();
        if (mapper.upsertResourceGrant(requestedId, tenantId, membershipId, resourceType.code(),
                resourceId, action.name(), actorAccountId, now) != 1) {
            throw new IllegalStateException("Unable to grant resource access");
        }
        ManagedResourceGrant grant = findActiveResourceGrant(membershipId, resourceType, resourceId, action)
                .orElseThrow(() -> new IllegalStateException("Granted resource access is unavailable"));
        if (mapper.advanceAuthorizationGeneration(tenantId, accountId, authorizationGeneration, now) != 1) {
            throw new IllegalStateException("Unable to advance authorization generation");
        }
        if (mapper.insertResourceGrantAudit(UUID.randomUUID(), tenantId, actorAccountId, sessionId,
                "RESOURCE_GRANT_CREATED", grant.id(), membershipId, resourceType.code(), resourceId,
                action.name(), null, now) != 1) {
            throw new IllegalStateException("Unable to audit resource grant");
        }
        return grant;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void revokeResourceGrant(ManagedResourceGrant grant, UUID actorAccountId, UUID sessionId,
                                    UUID accountId, long authorizationGeneration,
                                    String reason, Instant now) {
        if (mapper.revokeResourceGrant(grant.id(), actorAccountId, reason, now) != 1) {
            throw new IllegalStateException("Resource grant was concurrently revoked or missing");
        }
        if (mapper.advanceAuthorizationGeneration(grant.tenantId(), accountId,
                authorizationGeneration, now) != 1) {
            throw new IllegalStateException("Unable to advance authorization generation");
        }
        if (mapper.insertResourceGrantAudit(UUID.randomUUID(), grant.tenantId(), actorAccountId,
                sessionId, "RESOURCE_GRANT_REVOKED", grant.id(), grant.membershipId(),
                grant.resourceType().code(), grant.resourceId(), grant.action().name(), reason, now) != 1) {
            throw new IllegalStateException("Unable to audit resource grant revocation");
        }
    }

    private static ManagedResourceGrant toDomain(IamAdministrationMapper.GrantRow row) {
        return new ManagedResourceGrant(row.id(), row.tenantId(), row.membershipId(),
                ManagedResourceGrant.ResourceType.fromCode(row.resourceType()), row.resourceId(),
                com.accuenergy.octopus.common.security.ResourceAction.valueOf(row.action()),
                Optional.ofNullable(row.createdByAccountId()), row.createdAt(),
                Optional.ofNullable(row.revokedByAccountId()), Optional.ofNullable(row.revokedAt()),
                Optional.ofNullable(row.revokeReason()));
    }

    private static Set<String> parseStrings(String values) {
        if (values == null || values.isBlank()) return Set.of();
        return Set.of(values.split(","));
    }

    private static Set<UUID> parseUuids(String values) {
        if (values == null || values.isBlank()) return Set.of();
        return java.util.Arrays.stream(values.split(",")).map(UUID::fromString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
