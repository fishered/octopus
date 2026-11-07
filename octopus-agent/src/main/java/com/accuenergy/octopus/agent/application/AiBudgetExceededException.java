package com.accuenergy.octopus.agent.application;

public final class AiBudgetExceededException extends RuntimeException {
    public AiBudgetExceededException() {
        super("Tenant AI request budget is exhausted for the current minute");
    }
}
