package com.accuenergy.octopus.collect.infrastructure.influx;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.collect.application.port.TelemetrySinkPort;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public final class InfluxTelemetrySink implements TelemetrySinkPort {
    private final WriteApiBlocking writeApi;

    public InfluxTelemetrySink(WriteApiBlocking writeApi) {
        this.writeApi = writeApi;
    }

    @Override
    public void write(NormalizedTelemetry telemetry) {
        Point point = Point.measurement("meter_reading")
                .addTag("tenant_id", telemetry.tenantId().toString())
                .addTag("device_id", telemetry.deviceId().toString())
                .addTag("meter_id", telemetry.meterId().toString())
                .addTag("parameter_id", telemetry.parameterId().toString())
                .addField("event_id", telemetry.sourceEventId().toString())
                .addField("sequence", telemetry.sequence())
                .addField("model_version", telemetry.modelVersion())
                .addField("algorithm_version", telemetry.algorithmVersion())
                .addField("boot_id", telemetry.bootId())
                .addField("raw_value", telemetry.rawValue())
                .addField("delta", telemetry.delta())
                .addField("interval_accumulation", telemetry.intervalAccumulation())
                .addField("unit", telemetry.canonicalUnitCode())
                .addField("quality", telemetry.quality().toString())
                .time(epochNanos(telemetry.occurredAt()), WritePrecision.NS);
        writeApi.writePoint(point);
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }
}
