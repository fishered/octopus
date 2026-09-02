package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.common.security.ResourceGrant;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationSnapshot;
import com.accuenergy.octopus.mgmt.application.identity.RefreshTokenStore;
import com.accuenergy.octopus.mgmt.application.identity.SessionSnapshot;
import com.accuenergy.octopus.mgmt.application.identity.TokenIssuer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public final class RsaJwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder encoder;
    private final RefreshTokenStore refreshTokens;
    private final SecureRandom random;
    private final String issuer;
    private final String keyId;
    private final Duration accessLifetime;

    public RsaJwtTokenIssuer(JwtEncoder encoder, RefreshTokenStore refreshTokens, SecureRandom random,
                             String issuer, String keyId, Duration accessLifetime) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.random = random;
        this.issuer = issuer;
        this.keyId = keyId;
        this.accessLifetime = accessLifetime;
    }

    @Override
    public TokenPair issue(SessionSnapshot session, AuthorizationSnapshot authorization, Instant now) {
        return issuePair(session, authorization, now);
    }

    @Override
    public TokenPair rotate(SessionSnapshot session, AuthorizationSnapshot authorization, Instant now) {
        return issuePair(session, authorization, now);
    }

    private TokenPair issuePair(SessionSnapshot session, AuthorizationSnapshot authorization, Instant now) {
        Instant accessExpiresAt = now.plus(accessLifetime);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(session.accountId().toString()).audience(List.of("octopus-api"))
                .issuedAt(now).expiresAt(accessExpiresAt).id(UUID.randomUUID().toString())
                .claim("sid", session.sessionId().toString())
                .claim("account_id", session.accountId().toString())
                .claim("refresh_generation", session.refreshGeneration())
                .claim("account_generation", session.accountGeneration())
                .claim("authorization_generation", authorization.generation())
                .claim("principal_type", authorization.principalType().name())
                .claim("permissions", authorization.permissions())
                .claim("organization_scopes", authorization.organizationScopes().stream()
                        .map(RsaJwtTokenIssuer::encodeOrganizationScope).toList())
                .claim("resource_grants", authorization.resourceGrants().stream().map(RsaJwtTokenIssuer::encodeGrant).toList());
        authorization.tenantId().ifPresent(value -> claims.claim("tenant_id", value.toString()));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        String refreshToken = newRefreshToken();
        refreshTokens.replace(session.sessionId(), session.refreshGeneration(), refreshToken);
        return new TokenPair(accessToken, refreshToken, session.sessionId(), session.refreshGeneration(),
                accessExpiresAt, session.absoluteExpiresAt());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encodeGrant(ResourceGrant grant) {
        return grant.resourceType() + ":" + grant.resourceId() + ":" + grant.action().name();
    }

    private static Map<String, Object> encodeOrganizationScope(OrganizationAccessScope scope) {
        return Map.of("path", scope.organizationPath(),
                "accessLevel", scope.accessLevel().name(),
                "permissions", scope.permissions().stream().sorted().toList());
    }
}
