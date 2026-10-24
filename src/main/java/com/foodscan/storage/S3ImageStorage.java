package com.foodscan.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** S3-compatible storage (AWS S3, Cloudflare R2, MinIO) using presigned PUT URLs. */
class S3ImageStorage implements ImageStorage, AutoCloseable {

    private final StorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    S3ImageStorage(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        if (s3 == null || s3.bucket() == null || s3.bucket().isBlank()) {
            throw new IllegalStateException("foodscan.storage.s3.bucket must be set when storage type is s3");
        }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
        Region region = Region.of(s3.region());
        var clientBuilder = S3Client.builder().region(region).credentialsProvider(credentials);
        var presignerBuilder = S3Presigner.builder().region(region).credentialsProvider(credentials);
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            clientBuilder.endpointOverride(URI.create(s3.endpoint())).forcePathStyle(true);
            presignerBuilder.endpointOverride(URI.create(s3.endpoint()));
        }
        this.properties = properties;
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public Upload createUpload(String key, String contentType) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(p -> p
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(properties.s3().bucket())
                        .key(key)
                        .contentType(contentType)
                        .build()));
        return new Upload(key, presigned.url().toString(), "PUT", Map.of("Content-Type", contentType),
                presigned.expiration(), false);
    }

    @Override
    public byte[] read(String key, long maxBytes) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder().bucket(properties.s3().bucket()).key(key).build();
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(request)) {
            Long length = in.response().contentLength();
            if (length != null && length > maxBytes) {
                throw new IOException("Image is larger than " + maxBytes + " bytes");
            }
            return readLimited(in, maxBytes);
        }
    }

    private static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
        byte[] bytes = in.readNBytes((int) maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new IOException("Image is larger than " + maxBytes + " bytes");
        }
        return bytes;
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
