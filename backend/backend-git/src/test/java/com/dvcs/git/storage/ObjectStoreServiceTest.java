package com.dvcs.git.storage;

import com.dvcs.git.object.BlobObject;
import com.dvcs.git.object.IntegrityException;
import com.dvcs.git.object.SHA256Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ObjectStoreService}.
 *
 * <p>Mocks both {@link ObjectStoreBackend} and {@link StringRedisTemplate} so that
 * no real filesystem, S3, or Redis connection is required.
 */
@ExtendWith(MockitoExtension.class)
class ObjectStoreServiceTest {

    @Mock
    private ObjectStoreBackend backend;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ObjectStoreService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new ObjectStoreService(backend, redisTemplate);
    }

    // -------------------------------------------------------------------------
    // Test 1: write then read returns identical bytes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("write then read returns identical bytes")
    void writeThenReadReturnsIdenticalBytes() throws IOException {
        // Arrange
        String repoId = "repo-1";
        byte[] content = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        BlobObject blob = new BlobObject(content);
        byte[] serialized = blob.serialize();
        String expectedSha = SHA256Util.computeHex(serialized);

        // Redis returns no cached value on the first read
        when(valueOps.get(anyString())).thenReturn(null);
        // Backend returns the same bytes that were written
        when(backend.read(eq(repoId), eq(expectedSha))).thenReturn(serialized);

        // Act — write
        String returnedSha = service.writeObject(repoId, blob);

        // Assert — SHA returned by writeObject matches the blob's own SHA
        assertThat(returnedSha).isEqualTo(expectedSha);

        // Verify backend.write was called with the correct bytes
        ArgumentCaptor<byte[]> writtenBytes = ArgumentCaptor.forClass(byte[].class);
        verify(backend).write(eq(repoId), eq(expectedSha), writtenBytes.capture());
        assertThat(writtenBytes.getValue()).isEqualTo(serialized);

        // Act — read (cache miss path)
        byte[] readBytes = service.readObject(repoId, expectedSha);

        // Assert — bytes returned by readObject are identical to what was written
        assertThat(readBytes).isEqualTo(serialized);
    }

    // -------------------------------------------------------------------------
    // Test 2: SHA mismatch on read throws IntegrityException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SHA mismatch on read throws IntegrityException")
    void shaMismatchOnReadThrowsIntegrityException() throws IOException {
        // Arrange
        String repoId = "repo-2";
        byte[] originalContent = "original content".getBytes(StandardCharsets.UTF_8);
        BlobObject originalBlob = new BlobObject(originalContent);
        String correctSha = SHA256Util.computeHex(originalBlob.serialize());

        // Backend returns tampered bytes that do NOT match correctSha
        byte[] tamperedBytes = "tampered content".getBytes(StandardCharsets.UTF_8);

        // Redis cache miss so the service falls through to the backend
        when(valueOps.get(anyString())).thenReturn(null);
        when(backend.read(eq(repoId), eq(correctSha))).thenReturn(tamperedBytes);

        // Act & Assert — readObject must throw IntegrityException
        assertThatThrownBy(() -> service.readObject(repoId, correctSha))
                .isInstanceOf(IntegrityException.class)
                .hasMessageContaining(correctSha);
    }

    // -------------------------------------------------------------------------
    // Test 3: path traversal in SHA rejected with IllegalArgumentException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("path traversal '../' in SHA rejected with IllegalArgumentException on readObject")
    void pathTraversalForwardSlashInShaRejectedOnRead() {
        // Arrange
        String repoId = "repo-3";
        String maliciousSha = "../etc/passwd";

        // Act & Assert
        assertThatThrownBy(() -> service.readObject(repoId, maliciousSha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path-traversal");
    }

    @Test
    @DisplayName("path traversal '..\\' in SHA rejected with IllegalArgumentException on readObject")
    void pathTraversalBackSlashInShaRejectedOnRead() {
        // Arrange
        String repoId = "repo-3";
        String maliciousSha = "..\\windows\\system32";

        // Act & Assert
        assertThatThrownBy(() -> service.readObject(repoId, maliciousSha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path-traversal");
    }

    @Test
    @DisplayName("null SHA rejected with IllegalArgumentException on readObject")
    void nullShaRejectedOnRead() {
        assertThatThrownBy(() -> service.readObject("repo-3", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("empty SHA rejected with IllegalArgumentException on readObject")
    void emptyShaRejectedOnRead() {
        assertThatThrownBy(() -> service.readObject("repo-3", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Additional: Redis cache hit path returns bytes without calling backend
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("readObject returns cached bytes from Redis without calling backend")
    void readObjectReturnsCachedBytesFromRedis() throws IOException {
        // Arrange
        String repoId = "repo-4";
        byte[] content = "cached content".getBytes(StandardCharsets.UTF_8);
        BlobObject blob = new BlobObject(content);
        byte[] serialized = blob.serialize();
        String sha = SHA256Util.computeHex(serialized);

        // Simulate a Redis cache hit by returning Base64-encoded bytes
        String base64Encoded = java.util.Base64.getEncoder().encodeToString(serialized);
        when(valueOps.get("blob:" + repoId + ":" + sha)).thenReturn(base64Encoded);

        // Act
        byte[] result = service.readObject(repoId, sha);

        // Assert — bytes match and backend was never called
        assertThat(result).isEqualTo(serialized);
        verify(backend, never()).read(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Additional: writeObject caches bytes in Redis with correct key and TTL
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("writeObject caches serialized bytes in Redis with TTL 3600s")
    void writeObjectCachesBytesInRedis() throws IOException {
        // Arrange
        String repoId = "repo-5";
        byte[] content = "cache me".getBytes(StandardCharsets.UTF_8);
        BlobObject blob = new BlobObject(content);
        byte[] serialized = blob.serialize();
        String sha = SHA256Util.computeHex(serialized);
        String expectedKey = "blob:" + repoId + ":" + sha;
        String expectedEncoded = java.util.Base64.getEncoder().encodeToString(serialized);

        // Act
        service.writeObject(repoId, blob);

        // Assert — Redis set was called with the correct key, value, and TTL
        verify(valueOps).set(eq(expectedKey), eq(expectedEncoded), eq(Duration.ofSeconds(3600)));
    }
}
