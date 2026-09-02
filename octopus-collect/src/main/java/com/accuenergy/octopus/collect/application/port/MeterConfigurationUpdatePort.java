package com.accuenergy.octopus.collect.application.port;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;

public interface MeterConfigurationUpdatePort {
    void apply(MeterConfigurationChanged event);
}
