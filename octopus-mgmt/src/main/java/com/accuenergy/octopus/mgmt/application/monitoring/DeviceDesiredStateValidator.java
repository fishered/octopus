package com.accuenergy.octopus.mgmt.application.monitoring;

import java.util.UUID;

public interface DeviceDesiredStateValidator {
    ValidatedState validate(UUID deviceId, String stateJson);

    record ValidatedState(long modelVersion, String canonicalStateJson) { }
}
