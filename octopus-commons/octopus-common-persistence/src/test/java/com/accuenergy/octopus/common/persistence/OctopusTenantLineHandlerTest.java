package com.accuenergy.octopus.common.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.tenant.MissingTenantScopeException;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OctopusTenantLineHandlerTest {
    private final OctopusTenantLineHandler handler = new OctopusTenantLineHandler(Set.of("unit_catalog"));

    @Test
    void missingContextFailsClosed() {
        assertThrows(MissingTenantScopeException.class, handler::getTenantId);
    }

    @Test
    void emitsVerifiedTenantId() {
        TenantId id = new TenantId(UUID.randomUUID());
        TenantContext.run(new TenantScope.Scoped(id), () -> assertEquals("'" + id + "'", handler.getTenantId().toString()));
    }

    @Test
    void onlyExplicitGlobalTablesAreIgnored() {
        assertTrue(handler.ignoreTable("UNIT_CATALOG"));
        assertFalse(handler.ignoreTable("device"));
    }
}

