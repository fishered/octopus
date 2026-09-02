package com.accuenergy.octopus.common.storage;

import com.accuenergy.octopus.common.tenant.TenantId;
import java.io.InputStream;

public interface ObjectStorage {
    StoredObject put(TenantId tenantId, ObjectKey key, InputStream content, long contentLength, String contentType);
    InputStream get(TenantId tenantId, ObjectKey key);
    void delete(TenantId tenantId, ObjectKey key);
}

