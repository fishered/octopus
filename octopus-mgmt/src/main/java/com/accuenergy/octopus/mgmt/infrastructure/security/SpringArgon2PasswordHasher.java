package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.iam.PasswordHasher;
import java.nio.CharBuffer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public final class SpringArgon2PasswordHasher implements PasswordHasher {
    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hash(char[] password) {
        if (password == null || password.length < 12 || password.length > 1024) {
            throw new IllegalArgumentException("Password must contain between 12 and 1024 characters");
        }
        return encoder.encode(CharBuffer.wrap(password));
    }
}
