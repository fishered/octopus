package com.accuenergy.octopus.ca.infrastructure.security;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves CA database scope from the verified JWT; missing tenant claims never imply platform scope. */
public final class CaTenantScopeResolver {
    public TenantScope resolve(Jwt jwt, String targetTenantHeader, String supportReasonHeader) {
        Objects.requireNonNull(jwt, "jwt");
        AuthenticatedPrincipal.PrincipalType principalType = principalType(jwt);
        String targetTenant = normalize(targetTenantHeader);
        String supportReason = normalize(supportReasonHeader);
        String tokenTenant = normalize(jwt.getClaimAsString("tenant_id"));

        if (principalType == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            if (tokenTenant != null) {
                throw new IllegalArgumentException("Platform token must not contain tenant_id");
            }
            if (targetTenant != null) {
                requireSupportReason(supportReason);
                return new TenantScope.Scoped(TenantId.parse(targetTenant));
            }
            return new TenantScope.Platform(supportReason == null ? "CA platform administration" : supportReason);
        }

        if (targetTenant != null) {
            throw new IllegalArgumentException("Tenant override requires a platform administrator");
        }
        if (tokenTenant == null) {
            throw new IllegalArgumentException("Non-platform token requires tenant_id");
        }
        return new TenantScope.Scoped(TenantId.parse(tokenTenant));
    }

    private static AuthenticatedPrincipal.PrincipalType principalType(Jwt jwt) {
        String value = normalize(jwt.getClaimAsString("principal_type"));
        if (value == null) throw new IllegalArgumentException("JWT principal_type is required");
        try {
            return AuthenticatedPrincipal.PrincipalType.valueOf(value);
        } catch (IllegalArgumentException invalidType) {
            throw new IllegalArgumentException("JWT principal_type is invalid", invalidType);
        }
    }

    private static void requireSupportReason(String supportReason) {
        if (supportReason == null) {
            throw new IllegalArgumentException("Tenant override requires a support reason");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
