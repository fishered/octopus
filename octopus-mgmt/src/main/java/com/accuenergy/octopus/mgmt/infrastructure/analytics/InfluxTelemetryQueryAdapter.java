package com.accuenergy.octopus.mgmt.infrastructure.analytics;

import com.accuenergy.octopus.mgmt.application.analytics.TelemetryQueryPort;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public final class InfluxTelemetryQueryAdapter implements TelemetryQueryPort {
    private final QueryApi queryApi;
    private final String bucket;

    public InfluxTelemetryQueryAdapter(QueryApi queryApi,
            @Value("${octopus.influx.bucket:octopus-telemetry}") String bucket) {
        this.queryApi = queryApi;
        this.bucket = requireText(bucket, "bucket");
    }

    @Override
    public List<Point> query(TelemetryQuery query) {
        List<Point> points = new ArrayList<>();
        for (var table : queryApi.query(buildFlux(bucket, query))) {
            for (FluxRecord record : table.getRecords()) {
                if (record.getTime() == null || record.getValue() == null) continue;
                points.add(new Point(record.getTime(), decimal(record.getValue())));
            }
        }
        points.sort(Comparator.comparing(Point::bucketStart));
        return List.copyOf(points);
    }

    static String buildFlux(String bucket, TelemetryQuery query) {
        return "from(bucket: \"" + escape(bucket) + "\")\n"
                + "  |> range(start: time(v: \"" + query.start() + "\"), stop: time(v: \""
                + query.end() + "\"))\n"
                + "  |> filter(fn: (r) => r._measurement == \"meter_reading\""
                + " and r.tenant_id == \"" + query.tenantId() + "\""
                + " and r.meter_id == \"" + query.meterId() + "\""
                + " and r._field == \"" + query.field().storageField() + "\")\n"
                + "  |> aggregateWindow(every: " + query.bucket().toSeconds() + "s, fn: "
                + query.aggregation().fluxFunction() + ", createEmpty: false)\n"
                + "  |> keep(columns: [\"_time\", \"_value\"] )";
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number || value instanceof String) return new BigDecimal(value.toString());
        throw new IllegalStateException("Influx returned a non-numeric telemetry value");
    }

    private static String escape(String value) {
        return requireText(value, "value").replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
