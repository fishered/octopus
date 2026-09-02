package com.accuenergy.octopus.common.storage.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.storage.ObjectKey;
import com.accuenergy.octopus.common.storage.ObjectStorageException;
import com.accuenergy.octopus.common.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class S3ObjectStorageTest {
    @Test
    void prefixesTenantAndAppliesKmsEncryptionAcrossLifecycle() throws Exception {
        byte[] bytes = "certificate".getBytes(StandardCharsets.UTF_8);
        AtomicReference<PutObjectRequest> put = new AtomicReference<>();
        AtomicReference<GetObjectRequest> get = new AtomicReference<>();
        AtomicReference<DeleteObjectRequest> delete = new AtomicReference<>();
        S3Client client = fakeClient(bytes, put, get, delete);
        S3ObjectStorage storage = new S3ObjectStorage(client,
                new S3StorageOptions("octopus-artifacts", "/production/objects/",
                        S3StorageOptions.Encryption.KMS, "alias/octopus-storage"));
        TenantId tenant = new TenantId(UUID.randomUUID());
        TenantId otherTenant = new TenantId(UUID.randomUUID());
        ObjectKey key = new ObjectKey("certificates/device.pem");

        var stored = storage.put(tenant, key, new ByteArrayInputStream(bytes), bytes.length,
                "application/x-pem-file");
        assertArrayEquals(bytes, storage.get(tenant, key).readAllBytes());
        storage.delete(tenant, key);

        String expectedKey = "production/objects/" + tenant + "/certificates/device.pem";
        assertEquals(expectedKey, put.get().key());
        assertEquals(expectedKey, get.get().key());
        assertEquals(expectedKey, delete.get().key());
        assertEquals("octopus-artifacts", put.get().bucket());
        assertEquals(ServerSideEncryption.AWS_KMS, put.get().serverSideEncryption());
        assertEquals("alias/octopus-storage", put.get().ssekmsKeyId());
        assertEquals("etag-value", stored.etag());
        assertNotEquals(storage.storageKey(tenant, key), storage.storageKey(otherTenant, key));
    }

    @Test
    void validatesOptionsAndWrapsProviderFailures() {
        assertThrows(IllegalArgumentException.class, () -> new S3StorageOptions(
                "bucket", "prefix", S3StorageOptions.Encryption.KMS, null));
        S3Client failing = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("putObject")) {
                        throw S3Exception.builder().message("unavailable").statusCode(503).build();
                    }
                    if (method.getName().equals("close")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
        S3ObjectStorage storage = new S3ObjectStorage(failing,
                new S3StorageOptions("bucket", null, S3StorageOptions.Encryption.S3_MANAGED, null));

        assertThrows(ObjectStorageException.class, () -> storage.put(new TenantId(UUID.randomUUID()),
                new ObjectKey("firmware/image.bin"), new ByteArrayInputStream(new byte[1]),
                1, "application/octet-stream"));
    }

    private static S3Client fakeClient(byte[] content, AtomicReference<PutObjectRequest> put,
            AtomicReference<GetObjectRequest> get, AtomicReference<DeleteObjectRequest> delete) {
        return (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "putObject" -> {
                        put.set((PutObjectRequest) args[0]);
                        byte[] uploaded = ((software.amazon.awssdk.core.sync.RequestBody) args[1])
                                .contentStreamProvider().newStream().readAllBytes();
                        assertArrayEquals(content, uploaded);
                        yield PutObjectResponse.builder().eTag("\"etag-value\"").build();
                    }
                    case "getObject" -> {
                        get.set((GetObjectRequest) args[0]);
                        yield new ResponseInputStream<>(GetObjectResponse.builder()
                                .contentLength((long) content.length).build(),
                                AbortableInputStream.create(new ByteArrayInputStream(content)));
                    }
                    case "deleteObject" -> {
                        delete.set((DeleteObjectRequest) args[0]);
                        yield software.amazon.awssdk.services.s3.model.DeleteObjectResponse.builder().build();
                    }
                    case "serviceName" -> "s3";
                    case "close" -> null;
                    case "toString" -> "FakeS3Client";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
