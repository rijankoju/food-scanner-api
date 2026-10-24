package com.foodscan.storage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Storage keys look like {@code <kind>/<userId>/<uuid>.<ext>}, e.g.
 * {@code scans/1/....jpg} or {@code avatars/1/....jpg}. The user id in the key
 * is how ownership is checked when a scan or profile references an upload.
 */
public final class ImageKeys {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final Pattern KEY = Pattern.compile("(scans|avatars)/(\\d+)/[0-9a-f-]{36}\\.(jpg|png|webp)");

    /** What an upload is for; also the top-level folder of its key. */
    public enum Kind { scans, avatars }

    private ImageKeys() {
    }

    public static boolean isSupportedContentType(String contentType) {
        return EXTENSIONS.containsKey(contentType);
    }

    public static String newKey(Kind kind, long userId, String contentType) {
        return kind + "/" + userId + "/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
    }

    /** True if {@code key} is a well-formed key of this kind belonging to the user. */
    public static boolean isOwnedBy(String key, Kind kind, long userId) {
        var matcher = KEY.matcher(key);
        return matcher.matches() && matcher.group(1).equals(kind.name())
                && matcher.group(2).equals(Long.toString(userId));
    }

    /** True if {@code key} is a well-formed key of any kind belonging to the user. */
    public static boolean isOwnedBy(String key, long userId) {
        var matcher = KEY.matcher(key);
        return matcher.matches() && matcher.group(2).equals(Long.toString(userId));
    }

    /** Detects the image type from its first bytes, ignoring the claimed type. */
    public static Optional<String> sniffContentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return Optional.of("image/png");
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }
}
