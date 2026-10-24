package com.foodscan.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins allowed to call the API, e.g. the Expo web dev server.
 * Native apps don't send an Origin header, so CORS doesn't affect them.
 */
@ConfigurationProperties("foodscan.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
