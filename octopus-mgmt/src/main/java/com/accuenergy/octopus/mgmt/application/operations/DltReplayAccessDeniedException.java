package com.accuenergy.octopus.mgmt.application.operations;

public final class DltReplayAccessDeniedException extends RuntimeException {
    public DltReplayAccessDeniedException() {
        super("Only a platform administrator may replay dead-letter records");
    }
}
