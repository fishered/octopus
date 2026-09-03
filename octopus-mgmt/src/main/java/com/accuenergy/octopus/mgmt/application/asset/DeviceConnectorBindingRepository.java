package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.mgmt.domain.asset.DeviceConnectorBinding;
import java.util.Optional;
import java.util.UUID;

public interface DeviceConnectorBindingRepository {
    Optional<DeviceConnectorBinding> find(UUID deviceId);
    void insert(DeviceConnectorBinding binding);
    void update(DeviceConnectorBinding binding, long expectedVersion);
}
