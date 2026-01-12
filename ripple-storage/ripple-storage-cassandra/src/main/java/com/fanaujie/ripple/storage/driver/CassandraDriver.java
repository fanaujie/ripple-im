package com.fanaujie.ripple.storage.driver;

import com.datastax.oss.driver.api.core.CqlSession;

import java.net.InetSocketAddress;
import java.util.List;

public class CassandraDriver {
    public static CqlSession createCqlSession(
            List<String> cassandraContacts, String cassandraKeyspace, String localDatacenter) {
        return CqlSession.builder()
                .addContactPoints(
                        cassandraContacts.stream()
                                .map(
                                        p -> {
                                            String[] hostPort = p.split(":");
                                            return new InetSocketAddress(
                                                    hostPort[0], Integer.parseInt(hostPort[1]));
                                        })
                                .toList())
                .withKeyspace(cassandraKeyspace)
                .withLocalDatacenter(localDatacenter)
                .build();
    }
}
