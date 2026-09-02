package com.accuenergy.octopus.mgmt.interfaces.catalog;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.catalog.CatalogManagementService;
import com.accuenergy.octopus.mgmt.domain.catalog.DeviceType;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition;
import com.accuenergy.octopus.mgmt.domain.catalog.ThingModel;
import com.accuenergy.octopus.mgmt.domain.catalog.UnitDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
@RequestMapping("/api/v1/catalog")
public final class CatalogController {
    private final CatalogManagementService catalog;
    private final ObjectMapper objectMapper;

    public CatalogController(CatalogManagementService catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/device-types")
    @PreAuthorize("hasAuthority('PERM_catalog:configure') or hasAuthority('PERM_platform:all')")
    public DeviceTypeResponse createDeviceType(Authentication authentication,
                                               @Valid @RequestBody CreateDeviceTypeRequest request) {
        DeviceType deviceType = catalog.createDeviceType(principal(authentication),
                new CatalogManagementService.CreateDeviceType(request.code(), request.displayName(),
                        request.capabilities()));
        return deviceTypeResponse(deviceType);
    }

    @GetMapping("/device-types/{deviceTypeId}")
    @PreAuthorize("hasAuthority('PERM_catalog:view') or hasAuthority('PERM_platform:all')")
    public DeviceTypeResponse getDeviceType(Authentication authentication, @PathVariable UUID deviceTypeId) {
        return deviceTypeResponse(catalog.getDeviceType(principal(authentication), deviceTypeId));
    }

    @PostMapping("/units")
    @PreAuthorize("hasAuthority('PERM_platform:all')")
    public UnitResponse createUnit(Authentication authentication, @Valid @RequestBody CreateUnitRequest request) {
        UnitDefinition unit = catalog.createGlobalUnit(principal(authentication),
                new CatalogManagementService.CreateUnit(request.code(), request.symbol(), request.dimension(),
                        request.scale(), request.offset(), request.description()));
        return unitResponse(unit);
    }

    @GetMapping("/units/{unitId}")
    @PreAuthorize("hasAuthority('PERM_catalog:view') or hasAuthority('PERM_platform:all')")
    public UnitResponse getUnit(Authentication authentication, @PathVariable UUID unitId) {
        return unitResponse(catalog.getUnit(principal(authentication), unitId));
    }

    @PostMapping("/parameters")
    @PreAuthorize("hasAuthority('PERM_catalog:configure') or hasAuthority('PERM_platform:all')")
    public ParameterResponse createParameter(Authentication authentication,
                                             @Valid @RequestBody CreateParameterRequest request) {
        ParameterDefinition parameter = catalog.createParameter(principal(authentication),
                new CatalogManagementService.CreateParameter(request.code(), request.displayName(),
                        request.quantityKind(), request.valueSemantics(), request.dataType(),
                        Optional.ofNullable(request.canonicalUnitId()), Optional.ofNullable(request.decimalScale())));
        return parameterResponse(parameter);
    }

    @GetMapping("/parameters/{parameterId}")
    @PreAuthorize("hasAuthority('PERM_catalog:view') or hasAuthority('PERM_platform:all')")
    public ParameterResponse getParameter(Authentication authentication, @PathVariable UUID parameterId) {
        return parameterResponse(catalog.getParameter(principal(authentication), parameterId));
    }

    @PostMapping("/thing-models")
    @PreAuthorize("hasAuthority('PERM_catalog:configure') or hasAuthority('PERM_platform:all')")
    public ThingModelResponse createThingModel(Authentication authentication,
                                               @Valid @RequestBody CreateThingModelRequest request) {
        List<ThingModel.ParameterBinding> bindings = request.parameters().stream()
                .map(binding -> new ThingModel.ParameterBinding(binding.parameterId(), binding.unitId(),
                        binding.required(), binding.sortOrder(), binding.accessMode()))
                .toList();
        ThingModel model = catalog.createThingModel(principal(authentication),
                new CatalogManagementService.CreateThingModel(request.deviceTypeId(), request.code(),
                        request.displayName(), request.modelVersion(), request.schemaDocument().toString(), bindings));
        return modelResponse(model);
    }

    @PostMapping("/thing-models/{thingModelId}/publish")
    @PreAuthorize("hasAuthority('PERM_catalog:configure') or hasAuthority('PERM_platform:all')")
    public ThingModelResponse publishThingModel(Authentication authentication, @PathVariable UUID thingModelId) {
        return modelResponse(catalog.publishThingModel(principal(authentication), thingModelId));
    }

    @GetMapping("/thing-models/{thingModelId}")
    @PreAuthorize("hasAuthority('PERM_catalog:view') or hasAuthority('PERM_platform:all')")
    public ThingModelResponse getThingModel(Authentication authentication, @PathVariable UUID thingModelId) {
        return modelResponse(catalog.getThingModel(principal(authentication), thingModelId));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    private static UnitResponse unitResponse(UnitDefinition unit) {
        return new UnitResponse(unit.id(), unit.code(), unit.symbol(), unit.dimension(), unit.scale(),
                unit.offset(), unit.description().orElse(null));
    }

    private DeviceTypeResponse deviceTypeResponse(DeviceType deviceType) {
        try {
            return new DeviceTypeResponse(deviceType.id(), deviceType.tenantId().orElse(null), deviceType.code(),
                    deviceType.displayName(), objectMapper.readTree(deviceType.capabilitiesDocument()),
                    deviceType.status(), deviceType.createdAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored device type capabilities are invalid", exception);
        }
    }

    private static ParameterResponse parameterResponse(ParameterDefinition parameter) {
        return new ParameterResponse(parameter.id(), parameter.code(), parameter.displayName(),
                parameter.quantityKind(), parameter.valueSemantics(), parameter.dataType(),
                parameter.canonicalUnitId().orElse(null), parameter.decimalScale().orElse(null), parameter.status());
    }

    private static ThingModelResponse modelResponse(ThingModel model) {
        return new ThingModelResponse(model.id(), model.deviceTypeId(), model.code(), model.displayName(),
                model.modelVersion(), model.schemaDocument(), model.parameters(), model.status(),
                model.publishedAt().orElse(null), model.createdAt());
    }

    public record CreateUnitRequest(@NotBlank String code, @NotBlank String symbol,
                                    @NotBlank String dimension,
                                    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal scale,
                                    @NotNull BigDecimal offset, String description) { }

    public record UnitResponse(UUID id, String code, String symbol, String dimension,
                               BigDecimal scale, BigDecimal offset, String description) { }

    public record CreateDeviceTypeRequest(@NotBlank String code, @NotBlank String displayName,
                                          @NotNull JsonNode capabilities) { }

    public record DeviceTypeResponse(UUID id, UUID tenantId, String code, String displayName,
                                     JsonNode capabilities, DeviceType.Status status, Instant createdAt) { }

    public record CreateParameterRequest(@NotBlank String code, @NotBlank String displayName,
                                         @NotBlank String quantityKind,
                                         @NotNull ParameterDefinition.ValueSemantics valueSemantics,
                                         @NotNull ParameterDefinition.DataType dataType,
                                         UUID canonicalUnitId, @PositiveOrZero Integer decimalScale) { }

    public record ParameterResponse(UUID id, String code, String displayName, String quantityKind,
                                    ParameterDefinition.ValueSemantics valueSemantics,
                                    ParameterDefinition.DataType dataType, UUID canonicalUnitId,
                                    Integer decimalScale, ParameterDefinition.Status status) { }

    public record CreateThingModelRequest(@NotNull UUID deviceTypeId, @NotBlank String code,
                                          @NotBlank String displayName, @Positive long modelVersion,
                                          @NotNull JsonNode schemaDocument,
                                          @NotEmpty List<@Valid ParameterBindingRequest> parameters) { }

    public record ParameterBindingRequest(@NotNull UUID parameterId, @NotNull UUID unitId,
                                          boolean required, @PositiveOrZero int sortOrder,
                                          @NotNull ThingModel.AccessMode accessMode) { }

    public record ThingModelResponse(UUID id, UUID deviceTypeId, String code, String displayName,
                                     long modelVersion, String schemaDocument,
                                     List<ThingModel.ParameterBinding> parameters, ThingModel.Status status,
                                     Instant publishedAt, Instant createdAt) { }
}
