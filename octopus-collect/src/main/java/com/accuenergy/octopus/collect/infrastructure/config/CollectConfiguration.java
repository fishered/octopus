package com.accuenergy.octopus.collect.infrastructure.config;

import com.accuenergy.octopus.collect.application.TelemetryProcessor;
import com.accuenergy.octopus.collect.application.port.MeterConfigurationPort;
import com.accuenergy.octopus.collect.application.port.MeterStatePort;
import com.accuenergy.octopus.collect.application.port.NormalizedTelemetryPublisher;
import com.accuenergy.octopus.collect.application.port.QuarantinePort;
import com.accuenergy.octopus.collect.application.port.TelemetrySinkPort;
import com.accuenergy.octopus.collect.domain.meter.CumulativeAccumulator;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CollectConfiguration {
    @Bean
    CumulativeAccumulator cumulativeAccumulator() {
        return new CumulativeAccumulator();
    }

    @Bean
    TelemetryProcessor telemetryProcessor(MeterConfigurationPort configurations, MeterStatePort state,
                                          TelemetrySinkPort sink, NormalizedTelemetryPublisher publisher,
                                          QuarantinePort quarantine,
                                          CumulativeAccumulator accumulator) {
        return new TelemetryProcessor(configurations, state, sink, publisher, quarantine, accumulator);
    }

    @Bean(destroyMethod = "close")
    InfluxDBClient influxDBClient(@Value("${octopus.influx.url:http://localhost:8086}") String url,
                                  @Value("${octopus.influx.token:}") String token,
                                  @Value("${octopus.influx.org:octopus}") String org,
                                  @Value("${octopus.influx.bucket:octopus-telemetry}") String bucket) {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    @Bean
    WriteApiBlocking writeApiBlocking(InfluxDBClient client) {
        return client.getWriteApiBlocking();
    }
}
