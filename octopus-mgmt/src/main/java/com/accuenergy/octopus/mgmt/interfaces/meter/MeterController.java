package com.accuenergy.octopus.mgmt.interfaces.meter;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.meter.MeterManagementService;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meters")
public final class MeterController {
    private final MeterManagementService meters;
    private final ObjectMapper objectMapper;

    public MeterController(MeterManagementService meters, ObjectMapper objectMapper) {
        this.meters = meters;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_meter:create') or hasAuthority('PERM_platform:all')")
    public MeterResponse create(Authentication authentication, @Valid @RequestBody CreateMeterRequest request) {
        Meter meter = meters.create(principal(authentication), new MeterManagementService.CreateMeter(
                request.deviceId(), request.facilityId(), request.parameterId(), request.unitId(), request.code(),
                request.displayName(), request.kind(), request.calculationExpression() == null
                        ? null : request.calculationExpression().toString(),
                request.rolloverModulus(), request.decimalScale()));
        return response(meter);
    }

    @GetMapping("/{meterId}")
    @PreAuthorize("hasAuthority('PERM_meter:view') or hasAuthority('PERM_platform:all')")
    public MeterResponse get(Authentication authentication, @PathVariable UUID meterId) {
        return response(meters.get(principal(authentication), meterId));
    }

    private MeterResponse response(Meter meter) {
        JsonNode expression = meter.calculationExpression().map(this::readJson).orElse(null);
        return new MeterResponse(meter.id(), meter.deviceId(), meter.facilityId().orElse(null), meter.parameterId(),
                meter.unitId(), meter.code(), meter.displayName(), meter.kind(), expression,
                meter.rolloverModulus().orElse(null), meter.decimalScale(), meter.status(), meter.version(),
                meter.createdAt(), meter.updatedAt());
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException invalidStoredExpression) {
            throw new IllegalStateException("Stored meter expression is invalid", invalidStoredExpression);
        }
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CreateMeterRequest(@NotNull UUID deviceId, UUID facilityId,
                                     @NotNull UUID parameterId, @NotNull UUID unitId,
                                     @NotBlank String code, @NotBlank String displayName,
                                     @NotNull Meter.Kind kind, JsonNode calculationExpression,
                                     BigDecimal rolloverModulus, @PositiveOrZero Integer decimalScale) { }

    public record MeterResponse(UUID id, UUID deviceId, UUID facilityId, UUID parameterId, UUID unitId,
                                String code, String displayName, Meter.Kind kind,
                                JsonNode calculationExpression, BigDecimal rolloverModulus,
                                int decimalScale, Meter.Status status, long version,
                                Instant createdAt, Instant updatedAt) { }
}
