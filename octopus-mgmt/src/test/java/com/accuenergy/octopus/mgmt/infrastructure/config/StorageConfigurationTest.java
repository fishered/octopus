package com.accuenergy.octopus.mgmt.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.storage.local.LocalObjectStorage;
import com.accuenergy.octopus.common.storage.s3.S3StorageOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageConfigurationTest {
    private final StorageConfiguration configuration = new StorageConfiguration();

    @Test
    void createsLocalAdapterByConfiguredRoot(@TempDir Path root) {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRoot(root.toString());

        assertInstanceOf(LocalObjectStorage.class, configuration.localObjectStorage(properties));
    }

    @Test
    void createsS3OptionsAndClientWithoutStaticCredentials() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("octopus-artifacts");
        properties.getS3().setRegion("eu-west-1");
        properties.getS3().setPrefix("production/objects");
        properties.getS3().setEndpoint("http://localhost:9000");
        properties.getS3().setPathStyle(true);
        properties.getS3().setEncryption(S3StorageOptions.Encryption.KMS);
        properties.getS3().setKmsKeyId("alias/octopus-storage");

        S3StorageOptions options = configuration.objectStorageS3Options(properties);
        assertEquals("octopus-artifacts", options.bucket());
        assertEquals("production/objects", options.prefix());
        assertEquals(S3StorageOptions.Encryption.KMS, options.encryption());
        try (var client = configuration.objectStorageS3Client(properties)) {
            assertEquals("s3", client.serviceName());
        }
    }

    @Test
    void rejectsInvalidEndpoint() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setEndpoint("file:///tmp/bucket");

        assertThrows(IllegalArgumentException.class,
                () -> configuration.objectStorageS3Client(properties));
    }
}
