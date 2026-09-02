package com.accuenergy.octopus.ca.application;

import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Clock;
import java.util.UUID;

public final class RevokeDeviceIdentityService {
    private final DeviceIdentityRepository identities;
    private final Clock clock;

    public RevokeDeviceIdentityService(DeviceIdentityRepository identities, Clock clock) {
        this.identities = identities;
        this.clock = clock;
    }

    public DeviceIdentity revoke(UUID tenantId, UUID identityId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("Revocation reason is invalid");
        }
        DeviceIdentity identity = identities.findForTenant(tenantId, identityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown identity"));
        var now = clock.instant();
        identity.revoke(now);
        identities.revoke(identity, reason.strip(), now);
        return identity;
    }
}
