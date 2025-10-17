package com.fanaujie.ripple.storage.driver;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedisDriver {
    public static RedissonClient createRedissonClient(String host, int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return org.redisson.Redisson.create(config);
    }
}
