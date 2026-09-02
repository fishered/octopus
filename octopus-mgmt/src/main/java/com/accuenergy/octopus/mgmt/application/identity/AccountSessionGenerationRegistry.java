package com.accuenergy.octopus.mgmt.application.identity;

import java.util.UUID;

public interface AccountSessionGenerationRegistry {
    long current(UUID accountId, long persistentBaseline);
    long revokeAll(UUID accountId, long persistentBaseline);
}

