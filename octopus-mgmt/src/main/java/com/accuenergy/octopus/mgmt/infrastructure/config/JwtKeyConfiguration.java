package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.mgmt.application.identity.RefreshTokenStore;
import com.accuenergy.octopus.mgmt.application.identity.TokenIssuer;
import com.accuenergy.octopus.mgmt.infrastructure.security.HmacRefreshTokenHasher;
import com.accuenergy.octopus.mgmt.infrastructure.security.RsaJwtTokenIssuer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtKeyConfiguration {
    @Bean
    RSAKey rsaKey(@Value("${octopus.security.jwt.public-key}") Resource publicKeyResource,
                  @Value("${octopus.security.jwt.private-key}") Resource privateKeyResource,
                  @Value("${octopus.security.jwt.key-id}") String keyId) {
        try {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicKeyResource.getInputStream());
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateKeyResource.getInputStream());
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load JWT key material", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaKey, @Value("${octopus.security.jwt.issuer}") String issuer) {
        final RSAPublicKey publicKey;
        try {
            publicKey = rsaKey.toRSAPublicKey();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Invalid RSA public key", exception);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("octopus-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audience));
        return decoder;
    }

    @Bean
    HmacRefreshTokenHasher refreshTokenHasher(
            @Value("${octopus.security.refresh-token-hmac-key}") String base64Key) {
        return new HmacRefreshTokenHasher(base64Key);
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder encoder, RefreshTokenStore refreshTokens, SecureRandom random,
                            @Value("${octopus.security.jwt.issuer}") String issuer,
                            @Value("${octopus.security.jwt.key-id}") String keyId,
                            @Value("${octopus.security.jwt.access-lifetime:PT10M}") Duration accessLifetime) {
        return new RsaJwtTokenIssuer(encoder, refreshTokens, random, issuer, keyId, accessLifetime);
    }
}
