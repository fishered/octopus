package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmEvaluationService;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmManagementService;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlarmConfiguration {
    @Bean
    AlarmManagementService alarmManagementService(AlarmRepository alarms,
            AuthorizationPolicy authorization, Clock clock) {
        return new AlarmManagementService(alarms, authorization, clock);
    }

    @Bean
    AlarmEvaluationService alarmEvaluationService(AlarmRepository alarms) {
        return new AlarmEvaluationService(alarms);
    }
}
