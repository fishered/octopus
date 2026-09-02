package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthQueryMapper extends BaseMapper<AccountEntity> {
    CredentialRow findCredential(@Param("login") String normalizedLogin);
    CredentialRow findCredentialById(@Param("accountId") UUID accountId);
    AuthorizationBaseRow findTenantAuthorization(@Param("accountId") UUID accountId, @Param("tenantId") UUID tenantId);
    AuthorizationBaseRow findPlatformAuthorization(@Param("accountId") UUID accountId);
    List<String> findAllPermissions();
    List<String> findTenantPermissions(@Param("accountId") UUID accountId, @Param("tenantId") UUID tenantId);
    List<String> findOrganizationAdminPaths(@Param("accountId") UUID accountId,
                                            @Param("tenantId") UUID tenantId);
    List<PermissionScopeRow> findOperatorPermissionScopes(@Param("accountId") UUID accountId,
                                                           @Param("tenantId") UUID tenantId);
    List<ResourceGrantRow> findResourceGrants(@Param("accountId") UUID accountId, @Param("tenantId") UUID tenantId);
    void recordLoginFailure(@Param("accountId") UUID accountId, @Param("now") Instant now,
                            @Param("lockedUntil") Instant lockedUntil);
    void clearLoginFailures(@Param("accountId") UUID accountId, @Param("now") Instant now);
    int advanceSessionGeneration(@Param("accountId") UUID accountId, @Param("generation") long generation,
                                 @Param("now") Instant now);

    record CredentialRow(UUID accountId, String passwordHash, long sessionGeneration, String accountStatus,
                         int failedAttempts, Instant lockedUntil, UUID mfaFactorId, byte[] encryptedSecret) { }
    record AuthorizationBaseRow(String principalType, long authorizationGeneration) { }
    record PermissionScopeRow(String organizationPath, String permissionCode) { }
    record ResourceGrantRow(String resourceType, UUID resourceId, String action) { }
}
