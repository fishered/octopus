package com.accuenergy.octopus.control.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ControlSecurityConfigurationTest {
    @Test
    void mapsPluginReadPermissionAndScopeClaims() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("actor")
                .claim("permissions", List.of("device:configure"))
                .claim("scope", "iot:plugin:read").build();

        Set<String> authorities = new ControlSecurityConfiguration.PermissionAuthoritiesConverter().convert(jwt)
                .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertEquals(Set.of("PERM_device:configure", "SCOPE_iot:plugin:read"), authorities);
    }
}
