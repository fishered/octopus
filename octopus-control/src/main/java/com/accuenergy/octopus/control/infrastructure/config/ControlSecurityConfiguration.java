package com.accuenergy.octopus.control.infrastructure.config;

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
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** Secures operator-facing plugin discovery without changing device-edge protocols. */
@Configuration
@EnableMethodSecurity
public class ControlSecurityConfiguration {
    @Bean
    JwtDecoder controlJwtDecoder(@Value("${octopus.security.jwt.jwk-set-uri}") String jwkSetUri,
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
    JwtAuthenticationConverter controlJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new PermissionAuthoritiesConverter());
        return converter;
    }

    @Bean
    SecurityFilterChain controlSecurityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    static final class PermissionAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            Object permissions = jwt.getClaim("permissions");
            if (permissions instanceof Collection<?> values) {
                values.stream().map(Object::toString)
                        .map(value -> new SimpleGrantedAuthority("PERM_" + value))
                        .forEach(authorities::add);
            }
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) {
                java.util.Arrays.stream(scope.split("\\s+")).filter(value -> !value.isBlank())
                        .map(value -> new SimpleGrantedAuthority("SCOPE_" + value))
                        .forEach(authorities::add);
            }
            return authorities;
        }
    }
}
