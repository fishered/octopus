package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.security.ResourceGrant;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.identity.AccountCredential;
import com.accuenergy.octopus.mgmt.application.identity.AccountCredentialPort;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationSnapshot;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationSnapshotPort;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public final class MybatisIdentityAdapter implements AccountCredentialPort, AuthorizationSnapshotPort {
    private final AuthQueryMapper mapper;
    private final TransactionTemplate tenantTransactions;
    private final TransactionTemplate platformTransactions;
    private final Clock clock;
    private final AuthorizationGenerationRegistry authorizationGenerations;

    public MybatisIdentityAdapter(AuthQueryMapper mapper,
            @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantManager,
            @Qualifier("platformTransactionManager") PlatformTransactionManager platformManager,
            Clock clock, AuthorizationGenerationRegistry authorizationGenerations) {
        this.mapper = mapper;
        this.tenantTransactions = new TransactionTemplate(tenantManager);
        this.platformTransactions = new TransactionTemplate(platformManager);
        this.clock = clock;
        this.authorizationGenerations = authorizationGenerations;
    }

    @Override
    public Optional<AccountCredential> findByLogin(String normalizedLogin) {
        return platformTransactions.execute(status -> Optional.ofNullable(mapper.findCredential(normalizedLogin))
                .map(this::toCredential));
    }

    @Override
    public Optional<AccountCredential> findById(UUID accountId) {
        return platformTransactions.execute(status -> Optional.ofNullable(mapper.findCredentialById(accountId))
                .map(this::toCredential));
    }

    @Override
    public void recordFailure(String normalizedLogin) {
        platformTransactions.executeWithoutResult(status -> {
            AuthQueryMapper.CredentialRow row = mapper.findCredential(normalizedLogin);
            if (row == null) return;
            Instant now = clock.instant();
            int nextAttempt = row.failedAttempts() + 1;
            long delaySeconds = Math.min(900, nextAttempt < 5 ? 0 : 1L << Math.min(9, nextAttempt - 5));
            mapper.recordLoginFailure(row.accountId(), now, delaySeconds == 0 ? null : now.plusSeconds(delaySeconds));
        });
    }

    @Override
    public void clearFailures(String normalizedLogin) {
        platformTransactions.executeWithoutResult(status -> {
            AuthQueryMapper.CredentialRow row = mapper.findCredential(normalizedLogin);
            if (row != null) mapper.clearLoginFailures(row.accountId(), clock.instant());
        });
    }

    @Override
    public int advanceSessionGeneration(UUID accountId, long generation, Instant now) {
        return platformTransactions.execute(status -> mapper.advanceSessionGeneration(accountId, generation, now));
    }

    @Override
    public Optional<AuthorizationSnapshot> resolve(UUID accountId, Optional<UUID> requestedTenantId) {
        if (requestedTenantId.isEmpty()) {
            return platformTransactions.execute(status -> platformAuthorization(accountId));
        }
        UUID tenantId = requestedTenantId.orElseThrow();
        try {
            return TenantContext.call(new TenantScope.Scoped(new TenantId(tenantId)),
                    () -> tenantTransactions.execute(status -> tenantAuthorization(accountId, tenantId)));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Tenant authorization lookup failed", exception);
        }
    }

    private Optional<AuthorizationSnapshot> platformAuthorization(UUID accountId) {
        var base = mapper.findPlatformAuthorization(accountId);
        if (base == null) return Optional.empty();
        long generation = authorizationGenerations.current(accountId, Optional.empty(), base.authorizationGeneration());
        return Optional.of(new AuthorizationSnapshot(AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN,
                Optional.empty(), generation, Set.copyOf(mapper.findAllPermissions()),
                Set.of(), Set.of()));
    }

    private Optional<AuthorizationSnapshot> tenantAuthorization(UUID accountId, UUID tenantId) {
        var base = mapper.findTenantAuthorization(accountId, tenantId);
        if (base == null) return Optional.empty();
        Set<ResourceGrant> grants = mapper.findResourceGrants(accountId, tenantId).stream()
                .map(row -> new ResourceGrant(row.resourceType(), row.resourceId(), ResourceAction.valueOf(row.action())))
                .collect(Collectors.toUnmodifiableSet());
        long generation = authorizationGenerations.current(accountId, Optional.of(tenantId),
                base.authorizationGeneration());
        Set<OrganizationAccessScope> scopes = organizationScopes(accountId, tenantId);
        return Optional.of(new AuthorizationSnapshot(
                AuthenticatedPrincipal.PrincipalType.valueOf(base.principalType()), Optional.of(tenantId),
                generation, Set.copyOf(mapper.findTenantPermissions(accountId, tenantId)),
                scopes, grants));
    }

    private Set<OrganizationAccessScope> organizationScopes(UUID accountId, UUID tenantId) {
        Set<OrganizationAccessScope> scopes = new HashSet<>();
        mapper.findOrganizationAdminPaths(accountId, tenantId).forEach(path -> scopes.add(
                new OrganizationAccessScope(path,
                        OrganizationAccessScope.AccessLevel.ADMINISTRATOR, Set.of())));
        Map<String, Set<String>> operatorPermissions = new HashMap<>();
        for (AuthQueryMapper.PermissionScopeRow row : mapper.findOperatorPermissionScopes(accountId, tenantId)) {
            operatorPermissions.computeIfAbsent(row.organizationPath(), ignored -> new HashSet<>())
                    .add(row.permissionCode());
        }
        operatorPermissions.forEach((path, permissions) -> scopes.add(new OrganizationAccessScope(path,
                OrganizationAccessScope.AccessLevel.OPERATOR, permissions)));
        return Set.copyOf(scopes);
    }

    private AccountCredential toCredential(AuthQueryMapper.CredentialRow row) {
        AccountCredential.Status status = AccountCredential.Status.valueOf(row.accountStatus());
        if (row.lockedUntil() != null && row.lockedUntil().isAfter(clock.instant())) status = AccountCredential.Status.LOCKED;
        Optional<AccountCredential.TotpFactor> factor = row.mfaFactorId() == null ? Optional.empty()
                : Optional.of(new AccountCredential.TotpFactor(row.mfaFactorId(),
                Base64.getEncoder().encodeToString(row.encryptedSecret())));
        return new AccountCredential(row.accountId(), row.passwordHash(), row.sessionGeneration(), status, factor);
    }
}
