package com.nagriksetu.evidence;

import com.nagriksetu.config.NagrikProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Stores evidence in a private S3 bucket (AWS S3, MinIO, Cloudflare R2, DigitalOcean Spaces...).
 * The bucket must block public access: files are only served through the authorised evidence endpoint.
 * Credentials come from NAGRIK_S3_ACCESS_KEY / NAGRIK_S3_SECRET_KEY or the default AWS provider chain
 * (environment, instance profile, IRSA) when those are empty.
 */
@Service
@ConditionalOnProperty(name = "nagrik.storage.provider", havingValue = "s3")
public class S3EvidenceStorageService implements EvidenceStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3EvidenceStorageService.class);

    private final S3Client client;
    private final String bucket;
    private final String prefix;
    private final boolean customEndpoint;

    public S3EvidenceStorageService(NagrikProperties properties) {
        NagrikProperties.S3 cfg = properties.storage().s3();
        if (cfg == null || cfg.bucket() == null || cfg.bucket().isBlank()) {
            throw new IllegalStateException("NAGRIK_S3_BUCKET must be set when NAGRIK_STORAGE_PROVIDER=s3");
        }
        this.bucket = cfg.bucket();
        this.prefix = normalisePrefix(cfg.keyPrefix());
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(cfg.region()))
                .forcePathStyle(cfg.pathStyleAccess());
        this.customEndpoint = cfg.endpoint() != null && !cfg.endpoint().isBlank();
        if (customEndpoint) {
            builder.endpointOverride(URI.create(cfg.endpoint()));
        }
        if (cfg.accessKey() != null && !cfg.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        this.client = builder.build();
        log.info("Evidence storage: S3 bucket {} (prefix '{}')", bucket, prefix);
    }

    @PreDestroy
    void close() {
        client.close();
    }

    @Override
    public String providerName() {
        return "s3";
    }

    @Override
    public void store(String key, InputStream content, long sizeBytes, String contentType) throws IOException {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(key))
                .contentType(contentType)
                .contentLength(sizeBytes);
        if (!customEndpoint) {
            request.serverSideEncryption(ServerSideEncryption.AES256);
        }
        try {
            client.putObject(request.build(), RequestBody.fromInputStream(content, sizeBytes));
        } catch (S3Exception e) {
            throw new IOException("Could not store evidence in object storage", e);
        }
    }

    @Override
    public StoredObject open(String key) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
            return new StoredObject(stream, stream.response().contentLength());
        } catch (NoSuchKeyException e) {
            throw new java.nio.file.NoSuchFileException(key);
        } catch (S3Exception e) {
            throw new IOException("Could not read evidence from object storage", e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(b -> b.bucket(bucket).key(objectKey(key)));
        } catch (S3Exception e) {
            log.warn("Could not delete evidence object {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void listKeysOlderThan(Instant cutoff, Consumer<String> visitor) {
        client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .contents().forEach(obj -> {
                    if (obj.lastModified().isBefore(cutoff) && obj.key().startsWith(prefix)) {
                        String key = obj.key().substring(prefix.length());
                        try {
                            visitor.accept(StorageKeys.requireSafe(key));
                        } catch (IllegalArgumentException ignored) {
                            // Not a key this application generated.
                        }
                    }
                });
    }

    private String objectKey(String key) {
        return prefix + StorageKeys.requireSafe(key);
    }

    private static String normalisePrefix(String p) {
        if (p == null || p.isBlank()) {
            return "";
        }
        String trimmed = p.replaceAll("^/+", "");
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
