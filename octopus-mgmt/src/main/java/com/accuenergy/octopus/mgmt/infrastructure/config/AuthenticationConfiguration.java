package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.mgmt.application.identity.AccountCredentialPort;
import com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AuthenticationService;
import com.accuenergy.octopus.mgmt.application.identity.ForceLogoutService;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationSnapshotPort;
import com.accuenergy.octopus.mgmt.application.identity.MutableSessionRegistry;
import com.accuenergy.octopus.mgmt.application.identity.PasswordVerifier;
import com.accuenergy.octopus.mgmt.application.identity.RefreshTokenVerifier;
import com.accuenergy.octopus.mgmt.application.identity.RecoveryCodeVerifier;
import com.accuenergy.octopus.mgmt.application.identity.TokenIssuer;
import com.accuenergy.octopus.mgmt.application.identity.TotpEnrollmentVerifier;
import com.accuenergy.octopus.mgmt.application.identity.TotpLifecycleRepository;
import com.accuenergy.octopus.mgmt.application.identity.TotpLifecycleService;
import com.accuenergy.octopus.mgmt.application.identity.TotpSecretEncryptor;
import com.accuenergy.octopus.mgmt.application.identity.TotpVerifier;
import com.accuenergy.octopus.mgmt.application.iam.PasswordHasher;
import com.accuenergy.octopus.mgmt.infrastructure.security.AesGcmTotpSecretDecryptor;
import com.accuenergy.octopus.mgmt.infrastructure.security.Rfc6238TotpVerifier;
import com.accuenergy.octopus.mgmt.infrastructure.security.SpringArgon2PasswordVerifier;
import com.accuenergy.octopus.mgmt.infrastructure.security.TotpCounterStore;
import com.accuenergy.octopus.mgmt.infrastructure.security.TotpSecretDecryptor;
import java.time.Clock;
import java.time.Duration;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticationConfiguration {
    @Bean
    SpringArgon2PasswordVerifier passwordVerifier() {
        return new SpringArgon2PasswordVerifier();
    }

    @Bean
    AesGcmTotpSecretDecryptor totpSecretDecryptor(
            @Value("${octopus.security.totp-aes-key}") String base64Key) {
        return new AesGcmTotpSecretDecryptor(base64Key);
    }

    @Bean
    Rfc6238TotpVerifier totpVerifier(TotpSecretDecryptor secrets, TotpCounterStore counters) {
        return new Rfc6238TotpVerifier(secrets, counters);
    }

    @Bean
    AuthenticationService authenticationService(
            AccountCredentialPort accounts, SpringArgon2PasswordVerifier passwords, TotpVerifier totp,
            RecoveryCodeVerifier recoveryCodes,
            MutableSessionRegistry sessions, TokenIssuer tokens, AuthorizationSnapshotPort authorizations,
            RefreshTokenVerifier refreshTokens, Clock clock,
            AccountSessionGenerationRegistry accountGenerations,
            @Value("${octopus.security.session.idle-timeout:PT30M}") Duration idleTimeout,
            @Value("${octopus.security.session.absolute-timeout:PT8H}") Duration absoluteTimeout) {
        return new AuthenticationService(accounts, passwords, totp, recoveryCodes, sessions, tokens, authorizations,
                refreshTokens, accountGenerations, clock, idleTimeout, absoluteTimeout, passwords.dummyHash());
    }

    @Bean
    TotpLifecycleService totpLifecycleService(AccountCredentialPort accounts,
            SpringArgon2PasswordVerifier passwords, PasswordHasher passwordHasher,
            TotpSecretEncryptor secretEncryptor, TotpEnrollmentVerifier enrollmentVerifier,
            TotpVerifier totpVerifier, TotpLifecycleRepository repository,
            AccountSessionGenerationRegistry generations, Clock clock,
            @Value("${octopus.security.totp-enrollment-ttl:PT10M}") Duration enrollmentTtl,
            @Value("${octopus.security.totp-issuer:Octopus}") String issuer) {
        return new TotpLifecycleService(accounts, passwords, passwordHasher, secretEncryptor,
                enrollmentVerifier, totpVerifier, repository, generations, new SecureRandom(), clock,
                enrollmentTtl, issuer);
    }

    @Bean
    ForceLogoutService forceLogoutService(AccountCredentialPort accounts,
                                           AccountSessionGenerationRegistry generations,
                                           Clock clock) {
        return new ForceLogoutService(accounts, generations, clock);
    }
}
