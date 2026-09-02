package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.security.ResourceGrant;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.identity.SessionRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SessionAwareJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
    private final SessionRegistry sessions;
    private final Clock clock;
    private final AuthorizationGenerationRegistry authorizationGenerations;

    public SessionAwareJwtAuthenticationConverter(SessionRegistry sessions, Clock clock,
                                                   AuthorizationGenerationRegistry authorizationGenerations) {
        this.sessions = sessions;
        this.clock = clock;
        this.authorizationGenerations = authorizationGenerations;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        UUID sessionId = uuidClaim(jwt, "sid");
        UUID accountId = uuidClaim(jwt, "account_id");
        long refreshGeneration = longClaim(jwt, "refresh_generation");
        long accountGeneration = longClaim(jwt, "account_generation");
        long authorizationGeneration = longClaim(jwt, "authorization_generation");
        Optional<UUID> tokenTenantId = optionalString(jwt, "tenant_id").map(UUID::fromString);
        if (authorizationGenerations.current(accountId, tokenTenantId, authorizationGeneration)
                != authorizationGeneration) {
            throw new BadCredentialsException("Authorization has changed");
        }
        var session = sessions.find(sessionId)
                .filter(value -> value.accountId().equals(accountId))
                .filter(value -> value.tenantId().equals(tokenTenantId))
                .filter(value -> value.refreshGeneration() == refreshGeneration)
                .filter(value -> value.accountGeneration() == accountGeneration)
                .filter(value -> value.authorizationGeneration() == authorizationGeneration)
                .filter(value -> value.isUsableAt(clock.instant()))
                .orElseThrow(() -> new BadCredentialsException("Session is expired or revoked"));

        var type = AuthenticatedPrincipal.PrincipalType.valueOf(requiredString(jwt, "principal_type"));
        Optional<TenantId> tenantId = tokenTenantId.map(value -> new TenantId(value));
        Set<String> permissions = stringSet(jwt, "permissions");
        Set<OrganizationAccessScope> organizationScopes = parseOrganizationScopes(
                jwt.getClaim("organization_scopes"), tokenTenantId.isPresent());
        Set<ResourceGrant> grants = parseGrants(jwt.getClaimAsStringList("resource_grants"));
        var principal = new AuthenticatedPrincipal(accountId, session.sessionId(), accountGeneration, type,
                tenantId, permissions, Set.of(), organizationScopes, grants);
        Collection<GrantedAuthority> authorities = permissions.stream()
                .map(value -> (GrantedAuthority) new SimpleGrantedAuthority("PERM_" + value))
                .toList();
        var authentication = new JwtAuthenticationToken(jwt, authorities, accountId.toString());
        authentication.setDetails(principal);
        return authentication;
    }

    private static Set<ResourceGrant> parseGrants(List<String> encoded) {
        if (encoded == null) return Set.of();
        return encoded.stream().map(value -> {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) throw new BadCredentialsException("Invalid resource grant claim");
            return new ResourceGrant(parts[0], UUID.fromString(parts[1]), ResourceAction.valueOf(parts[2]));
        }).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<OrganizationAccessScope> parseOrganizationScopes(Object claim,
                                                                         boolean tenantToken) {
        if (claim == null) {
            if (tenantToken) throw new BadCredentialsException("Missing organization_scopes claim");
            return Set.of();
        }
        if (!(claim instanceof Collection<?> values)) {
            throw new BadCredentialsException("Invalid organization_scopes claim");
        }
        try {
            return values.stream().map(value -> parseOrganizationScope(value))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Invalid organization_scopes claim", exception);
        }
    }

    private static OrganizationAccessScope parseOrganizationScope(Object value) {
        if (!(value instanceof Map<?, ?> fields)) {
            throw new BadCredentialsException("Invalid organization scope entry");
        }
        Object path = fields.get("path");
        Object level = fields.get("accessLevel");
        Object permissions = fields.get("permissions");
        if (!(path instanceof String pathValue) || !(level instanceof String levelValue)
                || !(permissions instanceof Collection<?> permissionValues)
                || permissionValues.stream().anyMatch(item -> !(item instanceof String))) {
            throw new BadCredentialsException("Invalid organization scope entry");
        }
        Set<String> permissionSet = permissionValues.stream().map(String.class::cast)
                .collect(Collectors.toUnmodifiableSet());
        return new OrganizationAccessScope(pathValue,
                OrganizationAccessScope.AccessLevel.valueOf(levelValue), permissionSet);
    }

    private static Set<String> stringSet(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static UUID uuidClaim(Jwt jwt, String name) {
        return UUID.fromString(requiredString(jwt, name));
    }

    private static long longClaim(Jwt jwt, String name) {
        Number value = jwt.getClaim(name);
        if (value == null) throw new BadCredentialsException("Missing claim " + name);
        return value.longValue();
    }

    private static String requiredString(Jwt jwt, String name) {
        return optionalString(jwt, name).orElseThrow(() -> new BadCredentialsException("Missing claim " + name));
    }

    private static Optional<String> optionalString(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
