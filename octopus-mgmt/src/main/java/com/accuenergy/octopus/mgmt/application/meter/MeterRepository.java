package com.accuenergy.octopus.mgmt.application.meter;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface MeterRepository {
    boolean existsByCode(String code);
    Optional<DeviceContext> findDeviceContext(UUID deviceId);
    Optional<ParameterConfiguration> findParameterConfiguration(UUID parameterId, UUID unitId);
    boolean isBoundToDeviceModel(UUID deviceId, UUID parameterId, UUID unitId);
    boolean facilityExists(UUID facilityId);
    void insert(Meter meter, MeterConfigurationChanged event);
    Optional<MeterDetails> findDetails(UUID meterId);

    record DeviceContext(UUID tenantId, String organizationPath, long modelVersion) { }

    record ParameterConfiguration(ValueSemantics semantics, String sourceUnitCode,
                                  String canonicalUnitCode, BigDecimal scale, BigDecimal offset,
                                  int defaultDecimalScale) { }

    record MeterDetails(Meter meter, String organizationPath, String canonicalUnitCode,
            String effectiveZoneId) { }
}
