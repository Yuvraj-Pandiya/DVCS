package com.dvcs.git.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;

/**
 * S3-compatible implementation of {@link ObjectStoreBackend} backed by MinIO (or AWS S3).
 *
 * <p>Objects are stored using the same key layout as {@link LocalFsBackend}:
 * <pre>{@code {repoId}/objects/{sha[0..1]}/{sha[2..]}}</pre>
 *
 * <p>This bean is only activated when {@code storage.backend=s3} is set in the
 * application configuration, allowing the local filesystem backend to remain
 * active by default.
 *
 * <p>The {@link S3Client} is configured with:
 * <ul>
 *   <li>MinIO endpoint URL from {@code s3.endpoint} (e.g. {@code http://minio:9000})</li>
 *   <li>Path-style access enabled (required by MinIO)</li>
 *   <li>Static credentials from {@code s3.access-key} / {@code s3.secret-key}</li>
 *   <li>A fixed region ({@code us-east-1}) — MinIO ignores the region value</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "s3")
public class S3Backend implements ObjectStoreBackend {

    private final S3Client s3Client;
    private final String bucket;

    /**
     * Constructs an {@code S3Backend} and builds the {@link S3Client}.
     *
     * @param endpoint  MinIO / S3 endpoint URL, e.g. {@code http://minio:9000}
     * @param bucket    S3 bucket name that holds all object data
     * @param accessKey AWS / MinIO access key
     * @param secretKey AWS / MinIO secret key
     */
    public S3Backend(
            @Value("${s3.endpoint}") String endpoint,
            @Value("${s3.bucket}") String bucket,
            @Value("${s3.access-key}") String accessKey,
            @Value("${s3.secret-key}") String secretKey) {

        this.bucket = bucket;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // ObjectStoreBackend implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code PutObjectRequest} to upload {@code data} to S3/MinIO under
     * the key {@code {repoId}/objects/{sha[0..1]}/{sha[2..]}}.
     */
    @Override
    public void write(String repoId, String sha, byte[] data) throws IOException {
        String key = objectKey(repoId, sha);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentLength((long) data.length)
                            .build(),
                    RequestBody.fromBytes(data));
        } catch (Exception e) {
            throw new IOException("S3 write failed for key: " + key, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GetObjectRequest} to download the object bytes.
     *
     * @throws ObjectNotFoundException if the key does not exist in the bucket
     */
    @Override
    public byte[] read(String repoId, String sha) throws IOException {
        String key = objectKey(repoId, sha);
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ObjectNotFoundException(repoId, sha);
        } catch (Exception e) {
            throw new IOException("S3 read failed for key: " + key, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code HeadObjectRequest}; returns {@code false} if the key is absent.
     */
    @Override
    public boolean exists(String repoId, String sha) {
        String key = objectKey(repoId, sha);
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code DeleteObjectRequest}. If the key does not exist S3/MinIO
     * returns success, so this method completes without error in that case.
     */
    @Override
    public void delete(String repoId, String sha) throws IOException {
        String key = objectKey(repoId, sha);
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());
        } catch (Exception e) {
            throw new IOException("S3 delete failed for key: " + key, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code HeadObjectRequest} and returns the {@code Content-Length}.
     *
     * @throws ObjectNotFoundException if the key does not exist in the bucket
     */
    @Override
    public long size(String repoId, String sha) throws IOException {
        String key = objectKey(repoId, sha);
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());
            return response.contentLength();
        } catch (NoSuchKeyException e) {
            throw new ObjectNotFoundException(repoId, sha);
        } catch (Exception e) {
            throw new IOException("S3 size query failed for key: " + key, e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the S3 object key for a given {@code repoId} / {@code sha} pair,
     * mirroring the {@link LocalFsBackend} path layout.
     *
     * <p>Layout: {@code {repoId}/objects/{sha[0..1]}/{sha[2..]}}
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @return S3 object key string
     */
    String objectKey(String repoId, String sha) {
        String prefix = sha.substring(0, 2);
        String rest   = sha.substring(2);
        return repoId + "/objects/" + prefix + "/" + rest;
    }
}
