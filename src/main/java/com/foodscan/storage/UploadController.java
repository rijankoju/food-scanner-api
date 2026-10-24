package com.foodscan.storage;

import com.foodscan.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class UploadController {

    /** {@code kind} defaults to scans. */
    record UploadRequest(@NotBlank String contentType, ImageKeys.Kind kind) {
    }

    private final ImageStorage storage;
    private final StorageProperties properties;

    UploadController(ImageStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    /** Step 1 of a scan or profile photo change: get a URL to upload the photo to. */
    @PostMapping("/api/uploads/presign")
    ImageStorage.Upload presign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
        if (!ImageKeys.isSupportedContentType(request.contentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use image/jpeg, image/png or image/webp");
        }
        ImageKeys.Kind kind = request.kind() != null ? request.kind() : ImageKeys.Kind.scans;
        String key = ImageKeys.newKey(kind, CurrentUser.id(jwt), request.contentType());
        return storage.createUpload(key, request.contentType());
    }

    /** Upload target when using local storage (development only). */
    @PutMapping("/api/uploads/local/{kind}/{userId}/{file}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void uploadLocal(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind, @PathVariable String userId,
                     @PathVariable String file, HttpServletRequest request) throws IOException {
        if (!(storage instanceof LocalImageStorage local)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String key = kind + "/" + userId + "/" + file;
        if (!ImageKeys.isOwnedBy(key, CurrentUser.id(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your upload key");
        }
        byte[] bytes;
        try (InputStream in = request.getInputStream()) {
            bytes = in.readNBytes((int) properties.maxImageBytes() + 1);
        }
        if (bytes.length > properties.maxImageBytes()) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Image must be at most 5 MB");
        }
        if (ImageKeys.sniffContentType(bytes).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Not a JPEG, PNG or WebP image");
        }
        local.write(key, bytes);
    }
}
