package com.accuenergy.octopus.mgmt.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.security.ResourceGrant;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationSnapshot;
import com.accuenergy.octopus.mgmt.application.identity.RefreshTokenStore;
import com.accuenergy.octopus.mgmt.application.identity.SessionSnapshot;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class RsaJwtTokenIssuerTest {
    @Test
    void signsExpectedTenantAndSessionClaimsAndStoresOpaqueRefreshToken() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
        var store = new MemoryRefreshTokenStore();
        var issuer = new RsaJwtTokenIssuer(encoder, store, new SecureRandom(),
                "https://issuer.test", "test-key", Duration.ofMinutes(10));
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        var session = new SessionSnapshot(sessionId, accountId, Optional.of(tenantId), 2, 3, 4,
                SessionSnapshot.Status.ACTIVE, now.plusSeconds(1800), now.plusSeconds(3600));
        var authorization = new AuthorizationSnapshot(AuthenticatedPrincipal.PrincipalType.OPERATOR,
                Optional.of(tenantId), 4, Set.of("device:view"),
                Set.of(new OrganizationAccessScope("/root/east",
                        OrganizationAccessScope.AccessLevel.OPERATOR, Set.of("device:view"))),
                Set.of(new ResourceGrant("device", deviceId, ResourceAction.VIEW)));

        var tokens = issuer.issue(session, authorization, now);
        var decoded = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build().decode(tokens.accessToken());

        assertEquals(sessionId.toString(), decoded.getClaimAsString("sid"));
        assertEquals(tenantId.toString(), decoded.getClaimAsString("tenant_id"));
        assertEquals(2L, ((Number) decoded.getClaim("refresh_generation")).longValue());
        assertEquals(1, ((List<?>) decoded.getClaim("organization_scopes")).size());
        assertNull(decoded.getClaim("organization_paths"));
        assertEquals(2, tokens.refreshGeneration());
        assertTrue(store.matches(sessionId, 2, tokens.refreshToken()));
    }

    private static final class MemoryRefreshTokenStore implements RefreshTokenStore {
        private UUID sessionId;
        private long generation;
        private String token;
        public void replace(UUID sessionId, long refreshGeneration, String rawToken) {
            this.sessionId = sessionId; this.generation = refreshGeneration; this.token = rawToken;
        }
        public boolean matches(UUID sessionId, long refreshGeneration, String presentedToken) {
            return this.sessionId.equals(sessionId) && generation == refreshGeneration && token.equals(presentedToken);
        }
    }
}
