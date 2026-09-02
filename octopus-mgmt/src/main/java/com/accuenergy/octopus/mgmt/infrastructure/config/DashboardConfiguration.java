package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.dashboard.DashboardManagementService;
import com.accuenergy.octopus.mgmt.application.dashboard.DashboardRepository;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashboardConfiguration {
    @Bean
    DashboardManagementService dashboardManagementService(DashboardRepository dashboards,
            MeterRepository meters, AuthorizationPolicy authorization, Clock clock) {
        return new DashboardManagementService(dashboards, meters, authorization, clock);
    }
}
