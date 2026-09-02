package com.accuenergy.octopus.mgmt.application.identity;

public final class AuthenticationFailedException extends SecurityException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
