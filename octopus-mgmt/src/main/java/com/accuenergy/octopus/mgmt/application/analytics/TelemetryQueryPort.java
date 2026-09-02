package com.accuenergy.octopus.mgmt.application.analytics;

import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface TelemetryQueryPort {
    List<Point> query(TelemetryQuery query);

    record Point(Instant bucketStart, BigDecimal value) { }
}
