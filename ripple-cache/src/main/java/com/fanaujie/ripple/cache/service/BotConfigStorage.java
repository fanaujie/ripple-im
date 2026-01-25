package com.fanaujie.ripple.cache.service;

import com.fanaujie.ripple.storage.model.BotConfig;

public interface BotConfigStorage {

    BotConfig get(long userId) throws Exception;

    boolean isBot(long userId) throws Exception;

    void invalidate(long userId);
}
