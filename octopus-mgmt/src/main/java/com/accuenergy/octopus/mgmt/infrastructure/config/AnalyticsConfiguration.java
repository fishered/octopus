package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.mgmt.application.analytics.TelemetryAnalyticsService;
import com.accuenergy.octopus.mgmt.application.analytics.TelemetryQueryPort;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfiguration {
    @Bean(destroyMethod = "close")
    InfluxDBClient analyticsInfluxClient(@Value("${octopus.influx.url:http://localhost:8086}") String url,
            @Value("${octopus.influx.token:}") String token,
            @Value("${octopus.influx.org:octopus}") String org,
            @Value("${octopus.influx.bucket:octopus-telemetry}") String bucket) {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    @Bean
    QueryApi analyticsQueryApi(InfluxDBClient analyticsInfluxClient) {
        return analyticsInfluxClient.getQueryApi();
    }

    @Bean
    TelemetryAnalyticsService telemetryAnalyticsService(MeterRepository meters,
            TelemetryQueryPort telemetry, AuthorizationPolicy authorization) {
        return new TelemetryAnalyticsService(meters, telemetry, authorization);
    }
}
