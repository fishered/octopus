package com.accuenergy.octopus.mgmt.application.dashboard;

public final class DashboardVersionConflictException extends RuntimeException {
    public DashboardVersionConflictException() {
        super("Dashboard version does not match If-Match");
    }
}
