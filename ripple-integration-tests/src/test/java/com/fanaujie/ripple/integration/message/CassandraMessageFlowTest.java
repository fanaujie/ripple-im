package com.fanaujie.ripple.integration.message;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class CassandraMessageFlowTest extends AbstractMessageFlowTest {

    @Container
    protected static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:5.0.2").withInitScript("ripple.cql");

    protected CqlSession session;

    @Override
    protected void initializeStorage() {
        this.session =
                CqlSession.builder()
                        .addContactPoint(cassandraContainer.getContactPoint())
                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                        .build();

        this.storageFacade = new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    @Override
    protected void cleanupStorage() {
        if (session != null) {
            session.execute("TRUNCATE ripple.user");
            session.execute("TRUNCATE ripple.user_profile");
            session.execute("TRUNCATE ripple.user_relations");
            session.execute("TRUNCATE ripple.user_relation_version");
            session.execute("TRUNCATE ripple.user_blocked_by");
            session.execute("TRUNCATE ripple.user_conversations");
            session.execute("TRUNCATE ripple.user_conversations_version");
            session.execute("TRUNCATE ripple.user_messages");
            session.execute("TRUNCATE ripple.group_members");
            session.execute("TRUNCATE ripple.group_members_version");
            session.execute("TRUNCATE ripple.user_group");
            session.execute("TRUNCATE ripple.user_group_version");
            session.close();
        }
    }
}
