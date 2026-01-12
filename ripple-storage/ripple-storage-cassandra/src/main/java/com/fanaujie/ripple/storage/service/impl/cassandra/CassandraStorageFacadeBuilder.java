package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.Getter;

@Getter
public class CassandraStorageFacadeBuilder {
    private CqlSession session;

    public CassandraStorageFacadeBuilder cqlSession(CqlSession session) {
        this.session = session;
        return this;
    }

    public CassandraStorageFacade build() {
        return new CassandraStorageFacade(this);
    }
}
