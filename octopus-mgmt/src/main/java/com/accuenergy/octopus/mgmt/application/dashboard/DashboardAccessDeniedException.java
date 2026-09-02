package com.accuenergy.octopus.mgmt.application.dashboard;

public final class DashboardAccessDeniedException extends RuntimeException {
    public DashboardAccessDeniedException() {
        super("Dashboard access denied");
    }
}
