package com.accuenergy.octopus.ca.application;

import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Clock;
import java.util.UUID;

public final class ClaimDeviceIdentityService {
    private final DeviceIdentityRepository identities;
    private final BootstrapTokenService tokens;
    private final Clock clock;

    public ClaimDeviceIdentityService(DeviceIdentityRepository identities,
                                      BootstrapTokenService tokens, Clock clock) {
        this.identities = identities;
        this.tokens = tokens;
        this.clock = clock;
    }

    public DeviceIdentity claim(UUID identityId, UUID tenantId, String bootstrapToken) {
        String tokenHash = tokens.hash(bootstrapToken);
        return identities.claim(identityId, tokenHash, tenantId, clock.instant())
                .orElseThrow(() -> new SecurityException("Bootstrap credential is invalid, expired, or already used"));
    }
}
