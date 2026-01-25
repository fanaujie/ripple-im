package com.fanaujie.ripple.cache.service.impl;

import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.storage.model.BotConfig;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.api.RBucket;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.LocalCachedMapOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class RedisBotConfigStorage implements BotConfigStorage {
    private final Logger logger = LoggerFactory.getLogger(RedisBotConfigStorage.class);
    private final String botConfigPrefixKey = "BOT_CONFIG:";

    private final RedissonClient redissonClient;
    private final RippleStorageFacade storageFacade;
    private final RLocalCachedMap<String, byte[]> localCachedMap;
    private final Duration expireMinutes = Duration.ofMinutes(20);
    private final ObjectMapper objectMapper;

    public RedisBotConfigStorage(
            RedissonClient redissonClient, RippleStorageFacade storageFacade) {
        this.redissonClient = redissonClient;
        this.storageFacade = storageFacade;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        LocalCachedMapOptions<String, byte[]> options =
                LocalCachedMapOptions.<String, byte[]>name("botConfigCache")
                        .storeMode(LocalCachedMapOptions.StoreMode.LOCALCACHE)
                        .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
                        .cacheSize(500)
                        .timeToLive(Duration.ofMinutes(10))
                        .maxIdle(Duration.ofMinutes(3))
                        .syncStrategy(LocalCachedMapOptions.SyncStrategy.NONE);
        localCachedMap = redissonClient.getLocalCachedMap(options);
    }

    @Override
    public BotConfig get(long userId) throws Exception {
        String cacheKey = botConfigPrefixKey + userId;
        byte[] result = localCachedMap.get(cacheKey);
        if (result == null) {
            RBucket<byte[]> bucket = redissonClient.getBucket(cacheKey);
            byte[] cacheData = bucket.get();
            if (cacheData != null) {
                result = cacheData;
            } else {
                // Not in Redis cache, fetch from storage
                BotConfig botConfig = this.storageFacade.getBotConfig(userId);
                if (botConfig == null) {
                    // Not a bot, don't cache null to avoid complexity
                    return null;
                }
                result = this.objectMapper.writeValueAsBytes(botConfig);
                bucket.set(result, expireMinutes);
                localCachedMap.put(cacheKey, result);
                return botConfig;
            }
            localCachedMap.put(cacheKey, result);
        }
        return this.objectMapper.readValue(result, BotConfig.class);
    }

    @Override
    public boolean isBot(long userId) throws Exception {
        return get(userId) != null;
    }

    @Override
    public void invalidate(long userId) {
        String cacheKey = botConfigPrefixKey + userId;
        // Clear from local cache
        localCachedMap.remove(cacheKey);
        // Clear from Redis
        RBucket<byte[]> bucket = redissonClient.getBucket(cacheKey);
        bucket.delete();
        logger.debug("Invalidated bot config cache for userId: {}", userId);
    }
}
