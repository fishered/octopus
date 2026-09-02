package com.accuenergy.octopus.iot.spi;

/** Stable semantic message kinds exchanged by transport plugins. */
public enum MessageKind {
    TELEMETRY,
    SHADOW_REPORTED,
    COMMAND,
    COMMAND_RESULT,
    PRESENCE
}
