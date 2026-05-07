package com.dvcs.common.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Bucket4j distributed rate limiting backed by Redis.
 *
 * <p>Creates a Lettuce-based {@link ProxyManager} that stores bucket state in Redis,
 * ensuring rate limits are enforced consistently across all server instances (Req 17, Req 19.5).
 *
 * <p>Lettuce is used directly (rather than Spring Data Redis) because Bucket4j's
 * {@link LettuceBasedProxyManager} requires a raw Lettuce connection with a
 * {@code byte[]} value codec.
 */
@Configuration
public class Bucket4jConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Creates a Lettuce Redis client connected to the configured Redis instance.
     *
     * @return a {@link RedisClient} for Bucket4j use
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient() {
        RedisURI uri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        return RedisClient.create(uri);
    }

    /**
     * Creates a Lettuce connection with a {@code String} key / {@code byte[]} value codec,
     * as required by {@link LettuceBasedProxyManager}.
     *
     * @param redisClient the Lettuce Redis client
     * @return a stateful connection with the required codec
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(RedisClient redisClient) {
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * Creates the Bucket4j {@link ProxyManager} backed by Redis via Lettuce.
     *
     * <p>The proxy manager is used by {@link com.dvcs.common.security.RateLimitFilter}
     * to resolve or create per-key buckets stored in Redis.
     *
     * @param connection the Lettuce connection with String/byte[] codec
     * @return a {@link ProxyManager} keyed by {@link String}
     */
    @Bean
    public ProxyManager<String> bucket4jProxyManager(
            StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(java.time.Duration.ofHours(1)))
                .build();
    }
}
