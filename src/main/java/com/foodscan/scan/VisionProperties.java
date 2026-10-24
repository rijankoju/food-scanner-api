package com.foodscan.scan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code provider} selects which {@link FoodVisionClient} is active. */
@ConfigurationProperties("foodscan.vision")
public record VisionProperties(String provider, Gemini gemini, Claude claude) {

    public record Gemini(String apiKey, String model, String baseUrl, int maxOutputTokens) {
    }

    public record Claude(String apiKey, String model, String effort, long maxTokens, boolean refusalFallback) {
    }
}
