package com.accuenergy.octopus.control.infrastructure.config;

import com.accuenergy.octopus.control.application.ApplyDeviceCommandResultService;
import com.accuenergy.octopus.control.application.DevicePresenceService;
import com.accuenergy.octopus.control.application.DeviceShadowIngressService;
import com.accuenergy.octopus.control.application.DeviceMessagingUnavailableException;
import com.accuenergy.octopus.control.application.DispatchDeviceCommandService;
import com.accuenergy.octopus.control.application.TelemetryIngressService;
import com.accuenergy.octopus.control.application.port.DeviceCommandRepository;
import com.accuenergy.octopus.control.application.port.DeviceMessagingPort;
import com.accuenergy.octopus.control.application.port.DevicePresenceLeaseWriter;
import com.accuenergy.octopus.control.application.port.DeviceShadowIngressPublisher;
import com.accuenergy.octopus.control.application.port.DeviceShadowPayloadDecoder;
import com.accuenergy.octopus.control.application.port.TelemetryIngressPublisher;
import com.accuenergy.octopus.control.application.port.TelemetryPayloadDecoder;
import com.accuenergy.octopus.iot.spi.DeviceTransportPlugin;
import com.accuenergy.octopus.iot.spi.DeviceTransportPluginRegistry;
import com.accuenergy.octopus.iot.spi.DeviceBindingResolver;
import com.accuenergy.octopus.iot.spi.DeviceTransportRouter;
import com.accuenergy.octopus.iot.spi.ProtocolCodec;
import com.accuenergy.octopus.iot.spi.ProtocolCodecRegistry;
import com.accuenergy.octopus.iot.spi.MessageKind;
import com.accuenergy.octopus.iot.spi.OutboundMessage;
import com.accuenergy.octopus.iot.spi.PublishReceipt;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class ControlConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean
    ProtocolCodecRegistry protocolCodecRegistry(List<ProtocolCodec> codecs) {
        return new ProtocolCodecRegistry(codecs);
    }

    @Bean
    DeviceTransportPluginRegistry deviceTransportPluginRegistry(List<DeviceTransportPlugin> plugins,
            ProtocolCodecRegistry codecs) {
        return new DeviceTransportPluginRegistry(plugins, codecs);
    }

    @Bean
    @ConditionalOnMissingBean(DeviceBindingResolver.class)
    DeviceBindingResolver emptyDeviceBindingResolver() {
        return (tenantId, deviceId) -> java.util.Optional.empty();
    }

    @Bean
    DeviceTransportRouter deviceTransportRouter(DeviceTransportPluginRegistry registry,
            DeviceBindingResolver bindings,
            ProtocolCodecRegistry codecs,
            @Value("${octopus.iot.default-plugin:mqtt-json}") String defaultPlugin) {
        return new DeviceTransportRouter(registry, bindings, codecs, defaultPlugin);
    }

    @Bean
    TelemetryIngressService telemetryIngressService(TelemetryPayloadDecoder decoder,
            TelemetryIngressPublisher publisher, DevicePresenceService presence, Clock clock) {
        return new TelemetryIngressService(decoder, publisher, presence, clock);
    }

    @Bean
    DevicePresenceService devicePresenceService(DevicePresenceLeaseWriter leases,
            @Value("${octopus.presence.lease-duration:PT2M}") Duration leaseDuration) {
        return new DevicePresenceService(leases, leaseDuration);
    }

    @Bean
    DeviceShadowIngressService deviceShadowIngressService(DeviceShadowPayloadDecoder decoder,
            DeviceShadowIngressPublisher publisher, DevicePresenceService presence, Clock clock) {
        return new DeviceShadowIngressService(decoder, publisher, presence, clock);
    }

    @Bean
    DispatchDeviceCommandService dispatchDeviceCommandService(DeviceCommandRepository commands,
            DeviceMessagingPort messaging) {
        return new DispatchDeviceCommandService(commands, messaging);
    }

    @Bean
    @ConditionalOnMissingBean(DeviceMessagingPort.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(DeviceTransportPlugin.class)
    DeviceMessagingPort pluginDeviceMessagingPort(DeviceTransportRouter router) {
        return command -> {
            var envelope = command.envelope();
            var message = new OutboundMessage(MessageKind.COMMAND, envelope.tenantId(), envelope.deviceId(),
                    envelope.commandId(), envelope.operation(), envelope.payload(), envelope.requestedAt(),
                    envelope.expiresAt(), "application/json");
            try {
                PublishReceipt receipt = router.publish(message).toCompletableFuture().join();
                if (!receipt.correlationId().equals(envelope.commandId())) {
                    throw new DeviceMessagingUnavailableException("Plugin receipt does not match command");
                }
                return new DeviceMessagingPort.CommandReceipt(receipt.correlationId(),
                        DeviceMessagingPort.CommandReceipt.Status.valueOf(receipt.status().name()), receipt.acceptedAt());
            } catch (CompletionException failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new DeviceMessagingUnavailableException("IoT plugin publish failed", cause);
            }
        };
    }

    @Bean
    ApplyDeviceCommandResultService applyDeviceCommandResultService(DeviceCommandRepository commands) {
        return new ApplyDeviceCommandResultService(commands);
    }

    @Bean
    @ConditionalOnMissingBean(DeviceMessagingPort.class)
    DeviceMessagingPort unavailableDeviceMessagingPort() {
        return command -> { throw new DeviceMessagingUnavailableException("No device messaging adapter is enabled"); };
    }
}
