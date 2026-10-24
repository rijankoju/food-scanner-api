package com.foodscan.storage;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Stores meal photos. The app uploads directly to the URL from
 * {@link #createUpload}, so image bytes never pass through scan requests.
 */
public interface ImageStorage {

    /**
     * Where and how the client should upload. {@code requiresAuth} means the
     * client must also send its bearer token (true only for local storage).
     */
    record Upload(String key, String url, String method, Map<String, String> headers, Instant expiresAt,
                  boolean requiresAuth) {
    }

    Upload createUpload(String key, String contentType);

    /** Reads at most {@code maxBytes}; larger objects fail with an IOException. */
    byte[] read(String key, long maxBytes) throws IOException;
}
