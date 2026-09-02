package com.accuenergy.octopus.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextTest {
    @Test
    void failsClosedWithoutScope() {
        assertThrows(MissingTenantScopeException.class, TenantContext::requireCurrent);
    }

    @Test
    void bindsAndClearsScope() {
        TenantId id = new TenantId(UUID.randomUUID());
        TenantContext.run(new TenantScope.Scoped(id), () ->
                assertEquals(id, TenantContext.requireCurrent().tenantId().orElseThrow()));
        assertThrows(MissingTenantScopeException.class, TenantContext::requireCurrent);
    }

    @Test
    void platformScopeRequiresAuditReason() {
        assertThrows(IllegalArgumentException.class, () -> new TenantScope.Platform(" "));
    }

    @Test
    void clearsScopeAfterFailure() {
        TenantId id = new TenantId(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> TenantContext.run(
                new TenantScope.Scoped(id), () -> { throw new IllegalStateException("test"); }));
        assertThrows(MissingTenantScopeException.class, TenantContext::requireCurrent);
    }
}
