package com.fanaujie.ripple.integration.message;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class CassandraMessageFlowTest extends AbstractMessageFlowTest {

    @Container
    protected static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:5.0.2").withInitScript("ripple.cql");

    protected static CqlSession session;

    @BeforeAll
    static void initSession() {
        session =
                CqlSession.builder()
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

    @Override
    protected void initializeStorage() {
        this.storageFacade = new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    @Override
    protected void cleanupStorage() {}
}
