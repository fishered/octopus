package com.accuenergy.octopus.ca.infrastructure.config;

import com.accuenergy.octopus.ca.application.BootstrapTokenService;
import com.accuenergy.octopus.ca.application.CertificateSigningPort;
import com.accuenergy.octopus.ca.application.ClaimDeviceIdentityService;
import com.accuenergy.octopus.ca.application.DeviceIdentityRepository;
import com.accuenergy.octopus.ca.application.IssueOperationalCertificateService;
import com.accuenergy.octopus.ca.application.ManufactureDeviceIdentityService;
import com.accuenergy.octopus.ca.application.ProofOfPossessionPort;
import com.accuenergy.octopus.ca.application.RevokeDeviceIdentityService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaApplicationConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean
    ManufactureDeviceIdentityService manufactureDeviceIdentityService(DeviceIdentityRepository identities,
            BootstrapTokenService tokens, Clock clock) {
        return new ManufactureDeviceIdentityService(identities, tokens, clock);
    }

    @Bean
    ClaimDeviceIdentityService claimDeviceIdentityService(DeviceIdentityRepository identities,
            BootstrapTokenService tokens, Clock clock) {
        return new ClaimDeviceIdentityService(identities, tokens, clock);
    }

    @Bean
    IssueOperationalCertificateService issueOperationalCertificateService(DeviceIdentityRepository identities,
            ProofOfPossessionPort proof, CertificateSigningPort signing, Clock clock) {
        return new IssueOperationalCertificateService(identities, proof, signing, clock);
    }

    @Bean
    RevokeDeviceIdentityService revokeDeviceIdentityService(DeviceIdentityRepository identities, Clock clock) {
        return new RevokeDeviceIdentityService(identities, clock);
    }

    @Bean
    @ConditionalOnMissingBean(CertificateSigningPort.class)
    CertificateSigningPort unavailableSigningPort() {
        return request -> { throw new IllegalStateException("No CA signing adapter is configured"); };
    }
}
