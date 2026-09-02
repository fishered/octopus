package com.accuenergy.octopus.common.tenant;

public final class MissingTenantScopeException extends IllegalStateException {
    public MissingTenantScopeException(String message) {
        super(message);
    }
}

