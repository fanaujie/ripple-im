package com.fanaujie.ripple.storage.cache.impl;

import com.fanaujie.ripple.storage.cache.KvCache;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class RedissonKvCache implements KvCache {

    private final Logger logger = LoggerFactory.getLogger(RedissonKvCache.class);
    private final RedissonClient redissonClient;

    public RedissonKvCache(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void put(String key, byte[] value, long expireSeconds) {
        RBucket<byte[]> bucket = redissonClient.getBucket(key);
        try {
            bucket.setAsync(value, Duration.ofSeconds(expireSeconds)).get();
        } catch (CancellationException | ExecutionException | InterruptedException e) {
            logger.error("Failed to put value into cache for key: {}", key, e);
        }
    }

    @Override
    public byte[] getIfPresent(String key, long expireSeconds, Supplier<byte[]> loader) {
        RBucket<byte[]> bucket = redissonClient.getBucket(key);

        RFuture<byte[]> value = bucket.getAsync();
        byte[] v = null;
        try {
            v = value.get();
        } catch (CancellationException | ExecutionException | InterruptedException ignored) {
            logger.error("Failed to get value from cache for key: {}", key);
        }
        if (v == null && loader != null) {
            v = loader.get();
            if (v != null) {
                try {
                    bucket.setAsync(v, Duration.ofSeconds(expireSeconds)).get();
                } catch (CancellationException
                        | ExecutionException
                        | InterruptedException ignored) {
                    logger.error("Failed to put value into cache for key: {}", key);
                }
            }
        }
        return v;
    }

    @Override
    public void delete(String key) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        try {
            bucket.deleteAsync().get();
        } catch (CancellationException | ExecutionException | InterruptedException e) {
            logger.error("Failed to delete value from cache for key: {}", key, e);
        }
    }
}
