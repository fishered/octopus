package com.accuenergy.octopus.common.storage.s3;

import com.accuenergy.octopus.common.storage.ObjectKey;

public record S3StorageOptions(String bucket, String prefix, Encryption encryption, String kmsKeyId) {
    public S3StorageOptions {
        if (bucket == null || bucket.isBlank() || bucket.length() > 255) {
            throw new IllegalArgumentException("S3 bucket is required");
        }
        bucket = bucket.strip();
        prefix = normalizePrefix(prefix);
        encryption = encryption == null ? Encryption.S3_MANAGED : encryption;
        if (encryption == Encryption.KMS && (kmsKeyId == null || kmsKeyId.isBlank())) {
            throw new IllegalArgumentException("S3 KMS encryption requires kmsKeyId");
        }
        kmsKeyId = kmsKeyId == null || kmsKeyId.isBlank() ? null : kmsKeyId.strip();
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return "";
        new ObjectKey(normalized);
        return normalized;
    }

    public enum Encryption { NONE, S3_MANAGED, KMS }
}
