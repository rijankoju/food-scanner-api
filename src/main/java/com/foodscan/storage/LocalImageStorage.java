package com.foodscan.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Development storage: files on local disk, uploaded via {@link LocalUploadController}. */
class LocalImageStorage implements ImageStorage {

    private final Path root;
    private final StorageProperties properties;
    private final Clock clock;

    LocalImageStorage(StorageProperties properties, Clock clock) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Upload createUpload(String key, String contentType) {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/uploads/local/").path(key).toUriString();
        return new Upload(key, url, "PUT", Map.of("Content-Type", contentType),
                clock.instant().plus(properties.uploadUrlTtl()), true);
    }

    @Override
    public byte[] read(String key, long maxBytes) throws IOException {
        Path file = resolve(key);
        if (Files.size(file) > maxBytes) {
            throw new IOException("Image is larger than " + maxBytes + " bytes");
        }
        return Files.readAllBytes(file);
    }

    void write(String key, byte[] content) throws IOException {
        Path file = resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    /** Resolves a key under the root, rejecting anything that escapes it. */
    private Path resolve(String key) throws IOException {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("Invalid storage key");
        }
        return file;
    }
}
