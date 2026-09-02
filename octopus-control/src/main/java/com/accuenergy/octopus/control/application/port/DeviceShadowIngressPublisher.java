package com.accuenergy.octopus.control.application.port;

import com.accuenergy.octopus.api.control.DeviceShadowIngressFailure;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import java.util.concurrent.CompletionStage;

/** Completion means Kafka acknowledged the record according to producer durability settings. */
public interface DeviceShadowIngressPublisher {
    CompletionStage<Void> publishReported(DeviceShadowReported reported);
    CompletionStage<Void> publishQuarantine(DeviceShadowIngressFailure failure);
}
