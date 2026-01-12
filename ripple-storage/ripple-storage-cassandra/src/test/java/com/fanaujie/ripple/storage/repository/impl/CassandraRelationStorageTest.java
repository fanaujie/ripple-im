package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.repository.AbstractRelationStorageTest;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CassandraRelationStorageTest extends AbstractRelationStorageTest {

    @Container
    static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:5.0.2").withInitScript("ripple.cql");

    private CqlSession session;
    private RippleStorageFacade storageFacade;

    @BeforeEach
    void setUp() {
        this.session =
                CqlSession.builder()
                        .addContactPoint(cassandraContainer.getContactPoint())
                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                        .build();
        storageFacade = new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.execute("TRUNCATE ripple.user");
            session.execute("TRUNCATE ripple.user_profile");
            session.execute("TRUNCATE ripple.user_relations");
            session.execute("TRUNCATE ripple.user_relation_version");
            session.execute("TRUNCATE ripple.user_conversations");
            session.execute("TRUNCATE ripple.user_conversations_version");
            session.close();
        }
    }

    @Override
    protected RippleStorageFacade getStorageFacade() {
        return storageFacade;
    }
}