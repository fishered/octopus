package com.accuenergy.octopus.mgmt.interfaces.analytics;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.analytics.TelemetryAnalyticsService;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/analytics/meters/{meterId}")
public final class TelemetryAnalyticsController {
    private final TelemetryAnalyticsService analytics;

    public TelemetryAnalyticsController(TelemetryAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/series")
    @PreAuthorize("hasAuthority('PERM_analytics:view') or hasAuthority('PERM_platform:all')")
    public TelemetryAnalyticsService.Series series(Authentication authentication, @PathVariable UUID meterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam @Min(1) @Max(2_678_400) long bucketSeconds,
            @RequestParam TelemetryQuery.ValueField field,
            @RequestParam TelemetryQuery.Aggregation aggregation,
            @RequestParam(required = false) String zoneId) {
        return analytics.query(principal(authentication), meterId,
                new TelemetryAnalyticsService.QuerySeries(start, end, bucketSeconds, field, aggregation, zoneId));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }
}
