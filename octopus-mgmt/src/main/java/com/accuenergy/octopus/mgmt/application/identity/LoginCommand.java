package com.accuenergy.octopus.mgmt.application.identity;

import java.util.Optional;
import java.util.UUID;

public record LoginCommand(String login, char[] password, Optional<String> totpCode,
                           Optional<String> recoveryCode, Optional<UUID> tenantId) {
    public LoginCommand {
        password = password.clone();
        totpCode = Optional.ofNullable(totpCode).orElseGet(Optional::empty);
        recoveryCode = Optional.ofNullable(recoveryCode).orElseGet(Optional::empty);
        tenantId = Optional.ofNullable(tenantId).orElseGet(Optional::empty);
    }

    @Override
    public char[] password() {
        return password.clone();
    }
}
