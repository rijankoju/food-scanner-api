package com.foodscan.scan;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(VisionProperties.class)
class VisionConfig {

    /** Gemini is the default provider (free tier). */
    @Bean
    @ConditionalOnProperty(name = "foodscan.vision.provider", havingValue = "gemini", matchIfMissing = true)
    FoodVisionClient geminiFoodVisionClient(VisionProperties properties, ObjectMapper json) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        // Image analysis with thinking can take a while; don't hang forever either.
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.gemini().baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new GeminiFoodVisionClient(properties.gemini(), restClient, json);
    }
}
