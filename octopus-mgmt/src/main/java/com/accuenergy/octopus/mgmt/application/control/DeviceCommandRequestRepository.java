package com.accuenergy.octopus.mgmt.application.control;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import java.util.Optional;
import java.util.UUID;

public interface DeviceCommandRequestRepository {
    Optional<DeviceCommandRequest> findByIdempotency(UUID requestedBy, String idempotencyKey);
    Optional<CommandDetails> find(UUID commandId);
    void insert(DeviceCommandRequest request, DeviceCommandRequested event);
    void applyStatus(DeviceCommandStatusChanged event);

    record CommandDetails(DeviceCommandRequest request, String organizationPath) { }
}
