package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.repository.AbstractUserStorageTest;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CassandraUserStorageTest extends AbstractUserStorageTest {

    @Container
    static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:5.0.2").withInitScript("ripple.cql");

    private static CqlSession session;
    private RippleStorageFacade storageFacade;

    @BeforeAll
    static void initSession() {
        session = CqlSession.builder()
                .addContactPoint(cassandraContainer.getContactPoint())
                .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                .withKeyspace("ripple")
                .build();
    }

    @AfterAll
    static void closeSession() {
        if (session != null && !session.isClosed()) {
            session.close();
        }
    }

    @BeforeEach
    void setUp() {
        storageFacade = new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    @Override
    protected RippleStorageFacade getStorageFacade() {
        return storageFacade;
    }
}
