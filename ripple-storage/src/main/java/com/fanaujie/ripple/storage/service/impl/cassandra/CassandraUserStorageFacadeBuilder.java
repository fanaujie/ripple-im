package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.Getter;

@Getter
public class CassandraUserStorageFacadeBuilder {
    private CqlSession session;

    public CassandraUserStorageFacadeBuilder cqlSession(CqlSession session) {
        this.session = session;
        return this;
    }

    public CassandraUserStorageFacade build() {
        return new CassandraUserStorageFacade(this);
    }
}
