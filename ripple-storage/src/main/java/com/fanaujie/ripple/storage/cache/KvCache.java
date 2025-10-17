package com.fanaujie.ripple.storage.cache;

import java.util.function.Supplier;

public interface KvCache {
    void put(String key, byte[] value, long expireSeconds);

    byte[] getIfPresent(String key, long expireSeconds, Supplier<byte[]> loader);

    void delete(String key);
}
