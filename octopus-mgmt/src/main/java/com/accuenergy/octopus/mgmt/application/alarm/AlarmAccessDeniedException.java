package com.accuenergy.octopus.mgmt.application.alarm;

public final class AlarmAccessDeniedException extends RuntimeException {
    public AlarmAccessDeniedException() {
        super("Alarm access denied");
    }
}
