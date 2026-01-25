package com.fanaujie.ripple.integration.mock;

import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.storage.model.BotConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MockBotConfigStorage implements BotConfigStorage {

    private final Map<Long, BotConfig> bots = new ConcurrentHashMap<>();
    // Track bot IDs separately to support null config values (for testing edge cases)
    private final Set<Long> botIds = ConcurrentHashMap.newKeySet();

    @Override
    public BotConfig get(long userId) {
        return bots.get(userId);
    }

    @Override
    public boolean isBot(long userId) {
        return botIds.contains(userId);
    }

    @Override
    public void invalidate(long userId) {
        // No-op for mock
    }

    public void registerBot(long userId, BotConfig config) {
        botIds.add(userId);
        if (config != null) {
            bots.put(userId, config);
        }
    }

    public void clear() {
        bots.clear();
        botIds.clear();
    }
}
