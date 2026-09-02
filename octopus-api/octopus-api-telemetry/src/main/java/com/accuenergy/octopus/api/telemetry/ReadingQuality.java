package com.accuenergy.octopus.api.telemetry;

public enum ReadingQuality {
    GOOD,
    ESTIMATED,
    LATE,
    BOOT_CHANGED,
    OUT_OF_ORDER,
    GAP_DETECTED,
    RESET_DETECTED,
    ROLLOVER_DETECTED,
    INVALID
}
