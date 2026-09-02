package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Clock;
import java.util.UUID;

public final class ForceLogoutService {
    private final AccountCredentialPort accounts;
    private final AccountSessionGenerationRegistry generations;
    private final Clock clock;

    public ForceLogoutService(AccountCredentialPort accounts, AccountSessionGenerationRegistry generations,
                              Clock clock) {
        this.accounts = accounts;
        this.generations = generations;
        this.clock = clock;
    }

    public long revokeAll(UUID accountId) {
        AccountCredential account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account"));
        long nextGeneration = generations.revokeAll(accountId, account.sessionGeneration());
        if (accounts.advanceSessionGeneration(accountId, nextGeneration, clock.instant()) != 1) {
            throw new IllegalStateException("Unable to persist account session generation");
        }
        return nextGeneration;
    }
}
