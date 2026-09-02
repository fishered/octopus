package com.accuenergy.octopus.mgmt.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.SessionRegistry;
import com.accuenergy.octopus.mgmt.application.identity.SessionSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class SessionAwareJwtAuthenticationConverterTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID accountId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final SessionSnapshot session = new SessionSnapshot(sessionId, accountId,
            Optional.of(tenantId), 2, 3, 4, SessionSnapshot.Status.ACTIVE,
            NOW.plusSeconds(600), NOW.plusSeconds(1200));
    private final SessionAwareJwtAuthenticationConverter converter =
            new SessionAwareJwtAuthenticationConverter(new SessionRegistry() {
                public Optional<SessionSnapshot> find(UUID id) {
                    return sessionId.equals(id) ? Optional.of(session) : Optional.empty();
                }
                public void revoke(UUID id, String reason) { }
            }, Clock.fixed(NOW, ZoneOffset.UTC), new AuthorizationGenerationRegistry() {
                public long current(UUID id, Optional<UUID> tenant, long baseline) { return baseline; }
                public long revoke(UUID id, Optional<UUID> tenant, long baseline) { return baseline + 1; }
            });

    @Test
    void parsesMembershipBoundOrganizationScope() {
        Jwt jwt = jwt(List.of(Map.of(
                "path", "/root/east",
                "accessLevel", "OPERATOR",
                "permissions", List.of("device:view"))));

        var authentication = converter.convert(jwt);
        var principal = (AuthenticatedPrincipal) authentication.getDetails();
        OrganizationAccessScope scope = principal.organizationScopes().iterator().next();

        assertEquals("/root/east", scope.organizationPath());
        assertEquals(java.util.Set.of("device:view"), scope.permissions());
    }

    @Test
    void rejectsLegacyTenantTokenWithoutStructuredScopes() {
        assertThrows(BadCredentialsException.class, () -> converter.convert(jwt(null)));
    }

    private Jwt jwt(List<Map<String, Object>> scopes) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(accountId.toString())
                .issuedAt(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(300))
                .claim("sid", sessionId.toString())
                .claim("account_id", accountId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("refresh_generation", 2)
                .claim("account_generation", 3)
                .claim("authorization_generation", 4)
                .claim("principal_type", "OPERATOR")
                .claim("permissions", List.of("device:view"))
                .claim("resource_grants", List.of());
        if (scopes != null) builder.claim("organization_scopes", scopes);
        return builder.build();
    }
}
