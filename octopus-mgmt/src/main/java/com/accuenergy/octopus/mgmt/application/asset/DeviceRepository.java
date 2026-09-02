package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.mgmt.domain.asset.Device;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository {
    boolean existsByCode(String code);
    void insert(Device device);
    Optional<DeviceDetails> findDetails(UUID deviceId);
    Optional<String> findOrganizationPath(UUID organizationId);
    record DeviceDetails(Device device, String organizationPath) { }
}

