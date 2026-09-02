package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.storage.ObjectStorage;
import com.accuenergy.octopus.common.storage.local.LocalObjectStorage;
import com.accuenergy.octopus.common.storage.s3.S3ObjectStorage;
import com.accuenergy.octopus.common.storage.s3.S3StorageOptions;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    @ConditionalOnProperty(name = "octopus.storage.type", havingValue = "local", matchIfMissing = true)
    ObjectStorage localObjectStorage(StorageProperties properties) {
        String root = requireText(properties.getLocal().getRoot(), "octopus.storage.local.root");
        return new LocalObjectStorage(Path.of(root));
    }

    @Bean
    @ConditionalOnProperty(name = "octopus.storage.type", havingValue = "s3")
    S3StorageOptions objectStorageS3Options(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        return new S3StorageOptions(s3.getBucket(), s3.getPrefix(), s3.getEncryption(), s3.getKmsKeyId());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "octopus.storage.type", havingValue = "s3")
    S3Client objectStorageS3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        var builder = S3Client.builder()
                .region(Region.of(requireText(s3.getRegion(), "octopus.storage.s3.region")))
                .httpClientBuilder(ApacheHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyle())
                        .build());
        if (hasText(s3.getEndpoint())) {
            builder.endpointOverride(endpointUri(s3.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    @ConditionalOnProperty(name = "octopus.storage.type", havingValue = "s3")
    ObjectStorage s3ObjectStorage(S3Client objectStorageS3Client, S3StorageOptions objectStorageS3Options) {
        return new S3ObjectStorage(objectStorageS3Client, objectStorageS3Options);
    }

    private static URI endpointUri(String value) {
        URI endpoint = URI.create(value.strip());
        String scheme = endpoint.getScheme();
        if (scheme == null || endpoint.getHost() == null
                || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
            throw new IllegalArgumentException("octopus.storage.s3.endpoint must be an absolute HTTP(S) URI");
        }
        return endpoint;
    }

    private static String requireText(String value, String property) {
        if (!hasText(value)) throw new IllegalArgumentException(property + " must not be blank");
        return value.strip();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
