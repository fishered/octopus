package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.PasswordVerifier;
import java.nio.CharBuffer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public final class SpringArgon2PasswordVerifier implements PasswordVerifier {
    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());

    @Override
    public boolean matches(char[] presentedPassword, String encodedPassword) {
        return encoder.matches(CharBuffer.wrap(presentedPassword), encodedPassword);
    }

    public String dummyHash() {
        return dummyHash;
    }
}
