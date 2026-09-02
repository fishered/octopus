package com.accuenergy.octopus.mgmt.domain.identity;

public final class RefreshTokenReuseException extends SecurityException {
    public RefreshTokenReuseException(String message) {
        super(message);
    }
}

