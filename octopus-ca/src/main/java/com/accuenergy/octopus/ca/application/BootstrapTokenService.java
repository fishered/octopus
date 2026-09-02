package com.accuenergy.octopus.ca.application;

public interface BootstrapTokenService {
    IssuedToken issue();
    String hash(String rawToken);

    record IssuedToken(String rawToken, String tokenHash) { }
}
