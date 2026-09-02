package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.application.control.DeviceCommandManagementService;
import com.accuenergy.octopus.mgmt.application.control.DeviceCommandRequestRepository;
import com.accuenergy.octopus.mgmt.application.control.DeviceCommandStatusProjectionService;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredShadowRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeviceCommandConfiguration {
    @Bean
    DeviceCommandManagementService deviceCommandManagementService(DeviceRepository devices,
            DeviceCommandRequestRepository commands, AuthorizationPolicy authorization, Clock clock) {
        return new DeviceCommandManagementService(devices, commands, authorization, clock);
    }

    @Bean
    DeviceCommandStatusProjectionService deviceCommandStatusProjectionService(
            DeviceCommandRequestRepository commands, DeviceDesiredShadowRepository desired) {
        return new DeviceCommandStatusProjectionService(commands, desired);
    }
}
