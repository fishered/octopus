package com.accuenergy.octopus.control.application.port;

import com.accuenergy.octopus.api.control.DeviceCommandEnvelope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral port implemented by MQTT now and AWS IoT Core later. */
public interface DeviceMessagingPort {
    CommandReceipt send(DeviceCommand command);

    record DeviceCommand(DeviceCommandEnvelope envelope) {
        public DeviceCommand { Objects.requireNonNull(envelope, "envelope"); }
    }

    record CommandReceipt(UUID commandId, Status status, Instant acceptedAt) {
        public CommandReceipt {
            Objects.requireNonNull(commandId, "commandId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(acceptedAt, "acceptedAt");
        }
        public enum Status { ACCEPTED, REJECTED, EXPIRED }
    }
}
