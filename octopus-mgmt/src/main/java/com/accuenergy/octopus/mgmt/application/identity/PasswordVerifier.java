package com.accuenergy.octopus.mgmt.application.identity;

public interface PasswordVerifier {
    boolean matches(char[] presentedPassword, String encodedPassword);
}

