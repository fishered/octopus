package com.accuenergy.octopus.common.storage.local;

import com.accuenergy.octopus.common.storage.ObjectKey;
import com.accuenergy.octopus.common.storage.ObjectStorage;
import com.accuenergy.octopus.common.storage.ObjectStorageException;
import com.accuenergy.octopus.common.storage.StoredObject;
import com.accuenergy.octopus.common.tenant.TenantId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
    }

    @Override
    public StoredObject put(TenantId tenantId, ObjectKey key, InputStream content,
                            long contentLength, String contentType) {
        Objects.requireNonNull(content, "content must not be null");
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must be non-negative");
        Path target = resolve(tenantId, key);
        Path temporary = null;
        MessageDigest digest = sha256();
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".part");
            long written;
            try (DigestInputStream source = new DigestInputStream(content, digest)) {
                written = Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (written != contentLength) {
                throw new IllegalArgumentException("Content length mismatch");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredObject(key, written, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException exception) {
            throw new ObjectStorageException("Unable to store object", exception);
        } finally {
            deleteTemporaryQuietly(temporary);
        }
    }

    @Override
    public InputStream get(TenantId tenantId, ObjectKey key) {
        try {
            return Files.newInputStream(resolve(tenantId, key), StandardOpenOption.READ);
        } catch (IOException exception) {
            throw new ObjectStorageException("Unable to read object", exception);
        }
    }

    @Override
    public void delete(TenantId tenantId, ObjectKey key) {
        try {
            Files.deleteIfExists(resolve(tenantId, key));
        } catch (IOException exception) {
            throw new ObjectStorageException("Unable to delete object", exception);
        }
    }

    private Path resolve(TenantId tenantId, ObjectKey key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Path tenantRoot = root.resolve(tenantId.toString()).normalize();
        Path resolved = tenantRoot.resolve(key.value()).normalize();
        if (!resolved.startsWith(tenantRoot)) {
            throw new IllegalArgumentException("Object path escapes tenant root");
        }
        return resolved;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JDK", impossible);
        }
    }

    private static void deleteTemporaryQuietly(Path temporary) {
        if (temporary == null) return;
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The primary write result is more useful; orphan cleanup can be handled operationally.
        }
    }
}
