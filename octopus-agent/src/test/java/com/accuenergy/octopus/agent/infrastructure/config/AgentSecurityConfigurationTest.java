package com.accuenergy.octopus.agent.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AgentSecurityConfigurationTest {
    @Test
    void mapsPermissionAndOauthScopeClaims() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("actor")
                .claim("permissions", List.of("ai:integration:draft"))
                .claim("scope", "profile ai:integration:draft").build();

        Set<String> authorities = new AgentSecurityConfiguration.PermissionAuthoritiesConverter().convert(jwt)
                .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertEquals(Set.of("PERM_ai:integration:draft", "SCOPE_profile", "SCOPE_ai:integration:draft"), authorities);
    }
}
