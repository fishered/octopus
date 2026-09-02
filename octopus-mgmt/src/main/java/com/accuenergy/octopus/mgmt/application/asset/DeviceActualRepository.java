package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.mgmt.domain.asset.DeviceActual;
import java.util.Optional;
import java.util.UUID;

public interface DeviceActualRepository {
    Optional<DeviceContext> findDeviceContext(UUID deviceId);
    boolean hardwareSerialExists(String hardwareSerial);
    boolean deviceAlreadyCommissioned(UUID deviceId);
    boolean certificateInUse(UUID certificateId, UUID excludingActualId);
    void insert(DeviceActual actual);
    void update(DeviceActual actual);
    Optional<DeviceActualDetails> findDetailsByDeviceId(UUID deviceId);

    record DeviceContext(UUID tenantId, String organizationPath) { }
    record DeviceActualDetails(DeviceActual actual, String organizationPath) { }
}
