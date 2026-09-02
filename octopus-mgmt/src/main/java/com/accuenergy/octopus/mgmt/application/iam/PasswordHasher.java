package com.accuenergy.octopus.mgmt.application.iam;

public interface PasswordHasher {
    String hash(char[] password);
}
