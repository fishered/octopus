package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.storage.s3.S3StorageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("octopus.storage")
public class StorageProperties {
    private Type type = Type.LOCAL;
    private final Local local = new Local();
    private final S3 s3 = new S3();

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Local getLocal() {
        return local;
    }

    public S3 getS3() {
        return s3;
    }

    public enum Type {
        LOCAL,
        S3
    }

    public static final class Local {
        private String root = "./data/objects";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static final class S3 {
        private String bucket = "";
        private String region = "us-east-1";
        private String prefix = "";
        private String endpoint = "";
        private boolean pathStyle;
        private S3StorageOptions.Encryption encryption = S3StorageOptions.Encryption.S3_MANAGED;
        private String kmsKeyId = "";

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }

        public S3StorageOptions.Encryption getEncryption() {
            return encryption;
        }

        public void setEncryption(S3StorageOptions.Encryption encryption) {
            this.encryption = encryption;
        }

        public String getKmsKeyId() {
            return kmsKeyId;
        }

        public void setKmsKeyId(String kmsKeyId) {
            this.kmsKeyId = kmsKeyId;
        }
    }
}
