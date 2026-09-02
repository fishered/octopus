package com.accuenergy.octopus.mgmt.infrastructure.persistence.iam;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IamAdministrationMapper {
    long countAccountIdentity(@Param("loginName") String loginName, @Param("email") String email);
    int insertAccount(@Param("account") AccountRow account);
    AccountSecurityRow findAccountSecurity(@Param("accountId") UUID accountId);
    int updatePassword(@Param("accountId") UUID accountId, @Param("passwordHash") String passwordHash,
                       @Param("sessionGeneration") long sessionGeneration, @Param("now") Instant now);
    int insertPasswordAudit(@Param("id") UUID id, @Param("actorAccountId") UUID actorAccountId,
            @Param("sessionId") UUID sessionId, @Param("accountId") UUID accountId,
            @Param("eventType") String eventType, @Param("sessionGeneration") long sessionGeneration,
            @Param("reason") String reason, @Param("now") Instant now);
    int updateAccountStatus(@Param("accountId") UUID accountId, @Param("status") String status,
                            @Param("sessionGeneration") long sessionGeneration, @Param("now") Instant now);
    int insertAccountStatusAudit(@Param("id") UUID id,
            @Param("actorAccountId") UUID actorAccountId, @Param("sessionId") UUID sessionId,
            @Param("accountId") UUID accountId, @Param("previousStatus") String previousStatus,
            @Param("status") String status, @Param("sessionGeneration") long sessionGeneration,
            @Param("reason") String reason, @Param("now") Instant now);
    OrganizationRow findOrganization(@Param("organizationId") UUID organizationId);
    long countMembership(@Param("accountId") UUID accountId, @Param("organizationId") UUID organizationId);
    int insertMembership(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                         @Param("accountId") UUID accountId, @Param("organizationId") UUID organizationId,
                         @Param("now") Instant now);
    int initializeTenantSecurity(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId,
                                 @Param("now") Instant now);
    long countRoleCode(@Param("code") String code);
    List<String> findTenantAssignablePermissions(@Param("codes") Set<String> codes);
    List<PermissionRow> listTenantAssignablePermissions();
    List<MenuRow> listActiveMenus();
    int insertRole(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                   @Param("organizationId") UUID organizationId, @Param("code") String code,
                   @Param("displayName") String displayName, @Param("roleType") String roleType,
                   @Param("now") Instant now);
    int insertRolePermission(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId,
                             @Param("permissionCode") String permissionCode, @Param("now") Instant now);
    MembershipRow findMembership(@Param("membershipId") UUID membershipId);
    MembershipLifecycleRow findMembershipLifecycle(@Param("membershipId") UUID membershipId);
    RoleRow findRole(@Param("roleId") UUID roleId);
    List<String> findRolePermissions(@Param("roleId") UUID roleId);
    List<MembershipRow> findActiveRoleMemberships(@Param("roleId") UUID roleId);
    List<RoleViewRow> listRoles(@Param("organizationId") UUID organizationId);
    List<MembershipViewRow> listMemberships(@Param("organizationId") UUID organizationId);
    long countAssignment(@Param("membershipId") UUID membershipId, @Param("roleId") UUID roleId);
    int insertAssignment(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                         @Param("roleId") UUID roleId, @Param("now") Instant now);
    int deleteAssignment(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                         @Param("roleId") UUID roleId);
    int suspendMembership(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                          @Param("now") Instant now);
    int activateMembership(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                           @Param("now") Instant now);
    int advanceAuthorizationGeneration(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId,
                                       @Param("generation") long generation, @Param("now") Instant now);
    int insertMembershipRoleAudit(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("actorAccountId") UUID actorAccountId, @Param("sessionId") UUID sessionId,
            @Param("membershipId") UUID membershipId, @Param("roleId") UUID roleId,
            @Param("accountId") UUID accountId, @Param("reason") String reason,
            @Param("now") Instant now);
    int insertMembershipAudit(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("actorAccountId") UUID actorAccountId, @Param("sessionId") UUID sessionId,
            @Param("eventType") String eventType,
            @Param("membershipId") UUID membershipId, @Param("accountId") UUID accountId,
            @Param("organizationId") UUID organizationId, @Param("reason") String reason,
            @Param("now") Instant now);
    int deleteRolePermissions(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);
    int touchRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId,
                  @Param("now") Instant now);
    int retireRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId,
                   @Param("now") Instant now);
    int insertRoleAudit(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("actorAccountId") UUID actorAccountId, @Param("sessionId") UUID sessionId,
            @Param("eventType") String eventType, @Param("roleId") UUID roleId,
            @Param("affectedAccountCount") int affectedAccountCount,
            @Param("permissionCodes") String permissionCodes, @Param("reason") String reason,
            @Param("now") Instant now);
    GrantTargetRow findDeviceGrantTarget(@Param("resourceId") UUID resourceId);
    GrantTargetRow findFacilityGrantTarget(@Param("resourceId") UUID resourceId);
    GrantTargetRow findMeterGrantTarget(@Param("resourceId") UUID resourceId);
    GrantRow findActiveResourceGrant(@Param("grantId") UUID grantId);
    GrantRow findActiveResourceGrantByKey(@Param("membershipId") UUID membershipId,
            @Param("resourceType") String resourceType, @Param("resourceId") UUID resourceId,
            @Param("action") String action);
    List<GrantRow> listActiveResourceGrants(@Param("membershipId") UUID membershipId);
    int upsertResourceGrant(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("membershipId") UUID membershipId, @Param("resourceType") String resourceType,
            @Param("resourceId") UUID resourceId, @Param("action") String action,
            @Param("actorAccountId") UUID actorAccountId, @Param("now") Instant now);
    int revokeResourceGrant(@Param("grantId") UUID grantId,
            @Param("actorAccountId") UUID actorAccountId, @Param("reason") String reason,
            @Param("now") Instant now);
    int insertResourceGrantAudit(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("actorAccountId") UUID actorAccountId, @Param("sessionId") UUID sessionId,
            @Param("eventType") String eventType, @Param("grantId") UUID grantId,
            @Param("membershipId") UUID membershipId, @Param("resourceType") String resourceType,
            @Param("targetResourceId") UUID targetResourceId, @Param("action") String action,
            @Param("reason") String reason, @Param("now") Instant now);

    record AccountRow(UUID id, String loginName, String email, String phoneE164, String passwordHash,
                      String status, Instant passwordChangedAt, Instant createdAt, Instant updatedAt) { }
    record AccountSecurityRow(UUID accountId, String passwordHash, String status, long sessionGeneration) { }
    record OrganizationRow(UUID organizationId, String path) { }
    record MembershipRow(UUID membershipId, UUID accountId, UUID organizationId,
                         String organizationPath, long authorizationGeneration) { }
    record MembershipLifecycleRow(UUID membershipId, UUID accountId, UUID organizationId,
                                  String organizationPath, String status,
                                  long authorizationGeneration) { }
    record RoleRow(UUID roleId, UUID organizationId, String roleType) { }
    record RoleViewRow(UUID roleId, UUID organizationId, String code, String displayName,
                       String roleType, String status, long version, String permissionCodes) { }
    record MembershipViewRow(UUID membershipId, UUID accountId, UUID organizationId,
                             String organizationPath, String loginName, String email,
                             String status, String roleIds) { }
    record PermissionRow(String code, String resourceType, String action, String description) { }
    record MenuRow(UUID id, UUID parentId, String code, String route,
                   String requiredPermissionCode, int sortOrder) { }
    record GrantTargetRow(UUID organizationId, String organizationPath) { }
    record GrantRow(UUID id, UUID tenantId, UUID membershipId, String resourceType,
                    UUID resourceId, String action, UUID createdByAccountId, Instant createdAt,
                    UUID revokedByAccountId, Instant revokedAt, String revokeReason) { }
}
