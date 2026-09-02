package com.accuenergy.octopus.ca.infrastructure.config;

import com.accuenergy.octopus.ca.infrastructure.security.CaTenantSecurityFilter;
import com.accuenergy.octopus.ca.infrastructure.security.CaTenantScopeResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class CaSecurityConfiguration {
    @Bean
    CaTenantScopeResolver caTenantScopeResolver() {
        return new CaTenantScopeResolver();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${octopus.security.jwt.jwk-set-uri}") String jwkSetUri,
                          @Value("${octopus.security.jwt.issuer}") String issuer,
                          @Value("${octopus.security.jwt.audience:octopus-api}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter caJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new PermissionAuthoritiesConverter());
        return converter;
    }

    @Bean
    SecurityFilterChain caSecurityFilterChain(HttpSecurity http, CaTenantSecurityFilter tenantFilter,
                                              JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    private static final class PermissionAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            Object permissions = jwt.getClaim("permissions");
            if (permissions instanceof Collection<?> values) {
                values.stream().map(Object::toString)
                        .map(value -> new SimpleGrantedAuthority("PERM_" + value))
                        .forEach(authorities::add);
            }
            return authorities;
        }
    }
}
