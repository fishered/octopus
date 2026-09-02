package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.catalog.CatalogManagementService;
import com.accuenergy.octopus.mgmt.application.catalog.CatalogRepository;
import com.accuenergy.octopus.mgmt.application.meter.MeterManagementService;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfiguration {
    @Bean
    CatalogManagementService catalogManagementService(CatalogRepository catalog, Clock clock) {
        return new CatalogManagementService(catalog, clock);
    }

    @Bean
    MeterManagementService meterManagementService(MeterRepository meters,
            AuthorizationPolicy authorization, Clock clock) {
        return new MeterManagementService(meters, authorization, clock);
    }
}
