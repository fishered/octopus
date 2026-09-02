package com.accuenergy.octopus.common.storage;

public record StoredObject(ObjectKey key, long contentLength, String etag) {
}
