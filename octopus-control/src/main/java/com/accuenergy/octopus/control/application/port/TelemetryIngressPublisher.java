package com.accuenergy.octopus.control.application.port;

import com.accuenergy.octopus.api.telemetry.TelemetryIngressFailure;
import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import java.util.concurrent.CompletionStage;

/** Completion means Kafka has durably acknowledged the record according to producer policy. */
public interface TelemetryIngressPublisher {
    CompletionStage<Void> publishRaw(TelemetryReading reading);
    CompletionStage<Void> publishQuarantine(TelemetryIngressFailure failure);
}
