package com.accuenergy.octopus.mgmt.application.operations;

public final class DltReplayLimitExceededException extends RuntimeException {
    public DltReplayLimitExceededException(int maximum) {
        super("Replay chain has reached the configured maximum of " + maximum + " attempts");
    }
}
