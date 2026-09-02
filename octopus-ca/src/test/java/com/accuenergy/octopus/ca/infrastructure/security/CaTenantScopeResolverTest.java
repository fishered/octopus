package com.accuenergy.octopus.ca.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.tenant.TenantScope;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CaTenantScopeResolverTest {
    private final CaTenantScopeResolver resolver = new CaTenantScopeResolver();
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void scopedPrincipalRequiresItsTenantClaim() {
        Jwt token = token("OPERATOR", null);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(token, null, null));
    }

    @Test
    void scopedPrincipalCannotOverrideTenant() {
        Jwt token = token("ORGANIZATION_ADMIN", tenantId.toString());

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(token, UUID.randomUUID().toString(), "support ticket 42"));
    }

    @Test
    void platformPrincipalMaySelectTenantOnlyWithAuditReason() {
        Jwt token = token("PLATFORM_ADMIN", null);

        TenantScope scope = resolver.resolve(token, tenantId.toString(), "support ticket 42");

        assertEquals(new TenantScope.Scoped(new com.accuenergy.octopus.common.tenant.TenantId(tenantId)), scope);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(token, tenantId.toString(), null));
    }

    @Test
    void platformPrincipalCannotCarryTenantClaim() {
        Jwt token = token("PLATFORM_ADMIN", tenantId.toString());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(token, null, "maintenance"));
    }

    private static Jwt token(String principalType, String tenantId) {
        var builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("principal_type", principalType);
        if (tenantId != null) builder.claim("tenant_id", tenantId);
        return builder.build();
    }
}
