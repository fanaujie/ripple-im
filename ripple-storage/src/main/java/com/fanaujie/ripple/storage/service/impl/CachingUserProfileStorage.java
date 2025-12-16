package com.fanaujie.ripple.storage.service.impl;

import com.fanaujie.ripple.storage.cache.CachePrefixKey;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.UserProfileStorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.LocalCachedMapOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class CachingUserProfileStorage implements UserProfileStorage {
    private final Logger logger = LoggerFactory.getLogger(CachingUserProfileStorage.class);
    private final RedissonClient redissonClient;
    private final RippleStorageFacade storageFacade;
    private final RLocalCachedMap<String, byte[]> localCachedMap;
    private final Duration expireMinutes = Duration.ofMinutes(20);
    private ObjectMapper objectMapper = new ObjectMapper();

    public CachingUserProfileStorage(
            RedissonClient redissonClient, RippleStorageFacade storageFacade1) {
        this.redissonClient = redissonClient;
        this.storageFacade = storageFacade1;
        LocalCachedMapOptions<String, byte[]> options =
                LocalCachedMapOptions.<String, byte[]>name("userProfileCache")
                        .storeMode(LocalCachedMapOptions.StoreMode.LOCALCACHE)
                        .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
                        .cacheSize(1000)
                        .timeToLive(Duration.ofMinutes(10))
                        .maxIdle(Duration.ofMinutes(3))
                        .syncStrategy(LocalCachedMapOptions.SyncStrategy.NONE);
        localCachedMap = redissonClient.getLocalCachedMap(options);
    }

    @Override
    public UserProfile get(long key) throws Exception {
        String cacheKey = CachePrefixKey.USER_PROFILE.withSuffix(String.valueOf(key));
        byte[] result = localCachedMap.get(cacheKey);
        if (result == null) {

            RBucket<byte[]> bucket = redissonClient.getBucket(cacheKey);
            byte[] cacheData = bucket.get();
            if (cacheData != null) {
                result = cacheData;
            } else {
                // Not in Redis cache, fetch from storage
                try {
                    UserProfile userProfile = this.storageFacade.getUserProfile(key);
                    result = this.objectMapper.writeValueAsBytes(userProfile);
                    bucket.set(result, expireMinutes);
                    return userProfile;
                } catch (NotFoundUserProfileException e) {
                    return null;
                }
            }
            localCachedMap.put(cacheKey, result);
        }
        return this.objectMapper.readValue(result, UserProfile.class);
    }
}
