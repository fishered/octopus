package com.accuenergy.octopus.common.storage.s3;

import com.accuenergy.octopus.common.storage.ObjectKey;
import com.accuenergy.octopus.common.storage.ObjectStorage;
import com.accuenergy.octopus.common.storage.ObjectStorageException;
import com.accuenergy.octopus.common.storage.StoredObject;
import com.accuenergy.octopus.common.tenant.TenantId;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/** S3 adapter that physically prefixes every object with its immutable tenant UUID. */
public final class S3ObjectStorage implements ObjectStorage {
    private static final int MAX_S3_KEY_BYTES = 1024;
    private final S3Client client;
    private final S3StorageOptions options;

    public S3ObjectStorage(S3Client client, S3StorageOptions options) {
        this.client = Objects.requireNonNull(client, "client");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public StoredObject put(TenantId tenantId, ObjectKey key, InputStream content,
            long contentLength, String contentType) {
        Objects.requireNonNull(content, "content");
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must be non-negative");
        String storageKey = storageKey(tenantId, key);
        try {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(options.bucket())
                    .key(storageKey)
                    .contentLength(contentLength);
            if (contentType != null && !contentType.isBlank()) request.contentType(contentType.strip());
            applyEncryption(request);
            String etag = normalizeEtag(client.putObject(request.build(),
                    RequestBody.fromInputStream(content, contentLength)).eTag());
            return new StoredObject(key, contentLength, etag);
        } catch (SdkException failure) {
            throw new ObjectStorageException("Unable to store S3 object", failure);
        }
    }

    @Override
    public InputStream get(TenantId tenantId, ObjectKey key) {
        try {
            return client.getObject(GetObjectRequest.builder()
                    .bucket(options.bucket()).key(storageKey(tenantId, key)).build());
        } catch (SdkException failure) {
            throw new ObjectStorageException("Unable to read S3 object", failure);
        }
    }

    @Override
    public void delete(TenantId tenantId, ObjectKey key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(options.bucket()).key(storageKey(tenantId, key)).build());
        } catch (SdkException failure) {
            throw new ObjectStorageException("Unable to delete S3 object", failure);
        }
    }

    String storageKey(TenantId tenantId, ObjectKey key) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(key, "key");
        String tenantKey = tenantId + "/" + key.value();
        String resolved = options.prefix().isEmpty() ? tenantKey : options.prefix() + "/" + tenantKey;
        if (resolved.getBytes(StandardCharsets.UTF_8).length > MAX_S3_KEY_BYTES) {
            throw new IllegalArgumentException("Resolved S3 object key exceeds 1024 UTF-8 bytes");
        }
        return resolved;
    }

    private void applyEncryption(PutObjectRequest.Builder request) {
        switch (options.encryption()) {
            case NONE -> { }
            case S3_MANAGED -> request.serverSideEncryption(ServerSideEncryption.AES256);
            case KMS -> request.serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(options.kmsKeyId());
        }
    }

    private static String normalizeEtag(String etag) {
        if (etag == null) return "";
        String normalized = etag.strip();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
