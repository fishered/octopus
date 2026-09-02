package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.iam.IamAdministrationRepository;
import com.accuenergy.octopus.mgmt.application.iam.IamAdministrationService;
import com.accuenergy.octopus.mgmt.application.iam.PasswordHasher;
import com.accuenergy.octopus.mgmt.application.identity.AuthorizationGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.AccountSessionGenerationRegistry;
import com.accuenergy.octopus.mgmt.application.identity.PasswordVerifier;
import com.accuenergy.octopus.mgmt.infrastructure.security.SpringArgon2PasswordHasher;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamAdministrationConfiguration {
    @Bean PasswordHasher passwordHasher() { return new SpringArgon2PasswordHasher(); }

    @Bean
    IamAdministrationService iamAdministrationService(IamAdministrationRepository repository,
            PasswordHasher passwords, PasswordVerifier passwordVerifier, AuthorizationPolicy authorization,
            AuthorizationGenerationRegistry generations,
            AccountSessionGenerationRegistry accountSessionGenerations, Clock clock) {
        return new IamAdministrationService(repository, passwords, passwordVerifier, authorization, generations,
                accountSessionGenerations, clock);
    }
}
