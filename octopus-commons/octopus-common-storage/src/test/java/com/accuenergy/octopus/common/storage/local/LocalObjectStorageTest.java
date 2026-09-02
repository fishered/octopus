package com.accuenergy.octopus.common.storage.local;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.storage.ObjectKey;
import com.accuenergy.octopus.common.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {
    @TempDir Path root;

    @Test
    void storesObjectsBelowTenantPrefix() throws Exception {
        var storage = new LocalObjectStorage(root);
        var tenant = new TenantId(UUID.randomUUID());
        byte[] bytes = "certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        var stored = storage.put(tenant, new ObjectKey("certificates/device.pem"),
                new ByteArrayInputStream(bytes), bytes.length, "application/x-pem-file");

        assertEquals(64, stored.etag().length());
        assertArrayEquals(bytes, storage.get(tenant, stored.key()).readAllBytes());
    }

    @Test
    void rejectsTraversalAndAbsoluteKeys() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("../other-tenant/key"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("/absolute/key"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("folder\\key"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("folder//key"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("folder/./key"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("folder/file:stream"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("folder/CON.txt"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("folder/trailing."));
        assertEquals("reports/report..csv", new ObjectKey("reports/report..csv").value());
    }
}
