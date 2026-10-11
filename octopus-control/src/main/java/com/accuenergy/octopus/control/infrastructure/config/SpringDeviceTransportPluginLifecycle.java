package com.accuenergy.octopus.control.infrastructure.config;

import com.accuenergy.octopus.iot.spi.DeviceTransportPluginRuntime;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Bridges Spring Boot lifecycle events to the provider-neutral IoT plugin runtime. */
@Component
public final class SpringDeviceTransportPluginLifecycle implements SmartLifecycle {
    private final DeviceTransportPluginRuntime runtime;

    public SpringDeviceTransportPluginLifecycle(DeviceTransportPluginRuntime runtime) {
        this.runtime = runtime;
    }

    @Override public void start() { runtime.start(); }

    @Override public void stop() { runtime.stop(); }

    @Override public boolean isRunning() { return runtime.isRunning(); }

    @Override public boolean isAutoStartup() { return true; }

    @Override public int getPhase() { return -1_000; }
}
