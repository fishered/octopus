package com.accuenergy.octopus.mgmt.application.analytics;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuerySemantics;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TelemetryAnalyticsService {
    private final MeterRepository meters;
    private final TelemetryQueryPort telemetry;
    private final AuthorizationPolicy authorization;

    public TelemetryAnalyticsService(MeterRepository meters, TelemetryQueryPort telemetry,
            AuthorizationPolicy authorization) {
        this.meters = meters;
        this.telemetry = telemetry;
        this.authorization = authorization;
    }

    public Series query(AuthenticatedPrincipal principal, UUID meterId, QuerySeries request) {
        TenantId tenant = currentTenant();
        MeterRepository.MeterDetails details = meters.findDetails(meterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown meter"));
        Meter meter = details.meter();
        if (!meter.tenantId().equals(tenant.value()) || meter.status() != Meter.Status.ACTIVE) {
            throw new AnalyticsAccessDeniedException();
        }
        if (!authorization.isAllowed(principal, "analytics:view", ResourceAction.VIEW,
                new ProtectedResource(tenant, "meter", meterId, Optional.of(details.organizationPath())))) {
            throw new AnalyticsAccessDeniedException();
        }
        TelemetryQuerySemantics.validate(meter.semantics(), request.field());
        ZoneId zone;
        try {
            zone = ZoneId.of(request.zoneId() == null || request.zoneId().isBlank()
                    ? details.effectiveZoneId() : request.zoneId().strip());
        } catch (DateTimeException invalidZone) {
            throw new IllegalArgumentException("zoneId must be a valid IANA time zone", invalidZone);
        }
        TelemetryQuery query = new TelemetryQuery(tenant.value(), meterId, request.field(),
                request.aggregation(), request.start(), request.end(), Duration.ofSeconds(request.bucketSeconds()));
        List<SeriesPoint> points = telemetry.query(query).stream()
                .map(point -> new SeriesPoint(point.bucketStart(),
                        ZonedDateTime.ofInstant(point.bucketStart(), zone).toOffsetDateTime().toString(), point.value()))
                .toList();
        return new Series(meterId, meter.parameterId(), meter.semantics(), details.canonicalUnitCode(),
                meter.decimalScale(), zone.getId(), request.field(), request.aggregation(),
                request.start(), request.end(), request.bucketSeconds(), points);
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Analytics requires tenant scope"));
    }

    public record QuerySeries(Instant start, Instant end, long bucketSeconds,
            TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation, String zoneId) { }

    public record Series(UUID meterId, UUID parameterId, ValueSemantics semantics,
            String canonicalUnitCode, int decimalScale, String zoneId,
            TelemetryQuery.ValueField field, TelemetryQuery.Aggregation aggregation,
            Instant start, Instant end, long bucketSeconds, List<SeriesPoint> points) { }

    public record SeriesPoint(Instant bucketStart, String localBucketStart, BigDecimal value) { }
}
