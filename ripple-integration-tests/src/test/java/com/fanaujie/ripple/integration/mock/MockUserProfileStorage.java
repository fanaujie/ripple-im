package com.fanaujie.ripple.integration.mock;

import com.fanaujie.ripple.cache.service.UserProfileStorage;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockUserProfileStorage implements UserProfileStorage {

    private final RippleStorageFacade storageFacade;
    private final Map<Long, UserProfile> cache = new ConcurrentHashMap<>();

    public MockUserProfileStorage(RippleStorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @Override
    public UserProfile get(long userId) {
        return cache.computeIfAbsent(
                userId,
                id -> {
                    try {
                        return storageFacade.getUserProfile(id);
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    public void put(long userId, UserProfile profile) {
        cache.put(userId, profile);
    }

    public void clear() {
        cache.clear();
    }

    public void invalidate(long userId) {
        cache.remove(userId);
    }
}
