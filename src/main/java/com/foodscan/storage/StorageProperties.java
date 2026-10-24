package com.foodscan.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("foodscan.storage")
public record StorageProperties(String type, String localDir, long maxImageBytes, Duration uploadUrlTtl, S3 s3) {

    public record S3(String bucket, String endpoint, String region, String accessKey, String secretKey) {
    }
}
