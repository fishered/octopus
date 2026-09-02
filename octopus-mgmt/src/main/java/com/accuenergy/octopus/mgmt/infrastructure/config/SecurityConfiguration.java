package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.mgmt.application.identity.SessionRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import com.accuenergy.octopus.mgmt.infrastructure.security.SessionAwareJwtAuthenticationConverter;
import com.accuenergy.octopus.mgmt.infrastructure.security.TenantSecurityFilter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SessionAwareJwtAuthenticationConverter sessionAwareJwtAuthenticationConverter(
            SessionRegistry sessions, Clock clock, AuthorizationGenerationRegistry authorizationGenerations) {
        return new SessionAwareJwtAuthenticationConverter(sessions, clock, authorizationGenerations);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TenantSecurityFilter tenantFilter,
                                            SessionAwareJwtAuthenticationConverter converter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/oauth2/jwks",
                                "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
