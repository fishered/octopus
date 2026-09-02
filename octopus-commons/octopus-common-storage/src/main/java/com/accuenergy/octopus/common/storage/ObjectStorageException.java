package com.accuenergy.octopus.common.storage;

/** Provider-neutral storage failure exposed by object-storage adapters. */
public final class ObjectStorageException extends RuntimeException {
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
