package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.Getter;
import org.redisson.api.RedissonClient;

@Getter
public class CassandraUserStorageFacadeBuilder {
    private CqlSession session;
    private RedissonClient redissonClient;
    private long kvCacheExpireSeconds = 3600;

    public CassandraUserStorageFacadeBuilder cqlSession(CqlSession session) {
        this.session = session;
        return this;
    }

    public CassandraUserStorageFacadeBuilder redissonClient(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        return this;
    }

    public CassandraUserStorageFacadeBuilder kvCacheExpireSeconds(long kvCacheExpireSeconds) {
        this.kvCacheExpireSeconds = kvCacheExpireSeconds;
        return this;
    }

    public CassandraUserStorageFacade build() {
        if (session == null) {
            throw new IllegalStateException("CqlSession is required");
        }
        return new CassandraUserStorageFacade(this);
    }
}
