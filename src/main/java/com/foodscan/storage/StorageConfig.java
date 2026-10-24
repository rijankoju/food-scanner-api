package com.foodscan.storage;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    @Bean
    ImageStorage imageStorage(StorageProperties properties, Clock clock) {
        return switch (properties.type()) {
            case "local" -> new LocalImageStorage(properties, clock);
            case "s3" -> new S3ImageStorage(properties);
            default -> throw new IllegalStateException("Unknown foodscan.storage.type: " + properties.type());
        };
    }
}
