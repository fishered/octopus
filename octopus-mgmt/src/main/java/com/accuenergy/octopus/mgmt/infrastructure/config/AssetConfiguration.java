package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.asset.DeviceManagementService;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.application.asset.DeviceActualManagementService;
import com.accuenergy.octopus.mgmt.application.asset.DeviceActualRepository;
import com.accuenergy.octopus.mgmt.application.asset.FacilityManagementService;
import com.accuenergy.octopus.mgmt.application.asset.FacilityRepository;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceMonitoringService;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredShadowService;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredShadowRepository;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredStateValidator;
import com.accuenergy.octopus.mgmt.application.monitoring.DevicePresenceRepository;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceShadowProjectionService;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceShadowRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetConfiguration {
    @Bean AuthorizationPolicy authorizationPolicy() { return new AuthorizationPolicy(); }
    @Bean DeviceManagementService deviceManagementService(DeviceRepository devices,
            AuthorizationPolicy authorization, Clock clock) {
        return new DeviceManagementService(devices, authorization, clock);
    }
    @Bean FacilityManagementService facilityManagementService(FacilityRepository facilities,
            AuthorizationPolicy authorization, Clock clock) {
        return new FacilityManagementService(facilities, authorization, clock);
    }
    @Bean DeviceActualManagementService deviceActualManagementService(DeviceActualRepository actuals,
            AuthorizationPolicy authorization, Clock clock) {
        return new DeviceActualManagementService(actuals, authorization, clock);
    }
    @Bean DeviceMonitoringService deviceMonitoringService(DeviceRepository devices,
            DevicePresenceRepository presence, DeviceShadowRepository shadows,
            AuthorizationPolicy authorization) {
        return new DeviceMonitoringService(devices, presence, shadows, authorization);
    }
    @Bean DeviceShadowProjectionService deviceShadowProjectionService(DeviceShadowRepository shadows,
            DeviceDesiredShadowRepository desired) {
        return new DeviceShadowProjectionService(shadows, desired);
    }
    @Bean DeviceDesiredShadowService deviceDesiredShadowService(DeviceRepository devices,
            DeviceDesiredStateValidator validator, DeviceDesiredShadowRepository desired,
            AuthorizationPolicy authorization, Clock clock) {
        return new DeviceDesiredShadowService(devices, validator, desired, authorization, clock);
    }
}
