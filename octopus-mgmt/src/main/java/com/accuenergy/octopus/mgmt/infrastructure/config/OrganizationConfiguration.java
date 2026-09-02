package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.organization.OrganizationManagementService;
import com.accuenergy.octopus.mgmt.application.organization.OrganizationRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrganizationConfiguration {
    @Bean
    OrganizationManagementService organizationManagementService(OrganizationRepository organizations,
            AuthorizationPolicy authorization, Clock clock) {
        return new OrganizationManagementService(organizations, authorization, clock);
    }
}
