package com.fanaujie.ripple.storage.cassandra.spi;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import com.fanaujie.ripple.storage.spi.RippleStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CassandraStorageProvider implements RippleStorageProvider {
    private static final Logger logger = LoggerFactory.getLogger(CassandraStorageProvider.class);

    @Override
    public RippleStorageFacade create(Function<String, String> propertyLoader) {
        logger.info("Initializing CassandraStorageProvider...");

        String contactPointsStr =
                getProperty(propertyLoader, "cassandra.contact-points", "CASSANDRA_CONTACT_POINTS");
        logger.info("Resolved 'cassandra.contact-points': {}", contactPointsStr);
        if (contactPointsStr == null || contactPointsStr.isEmpty()) {
            contactPointsStr = "localhost:9042";
            logger.warn(
                    "No Cassandra contact points configured, using default: {}", contactPointsStr);
        }

        String keyspace =
                getProperty(propertyLoader, "cassandra.keyspace-name", "CASSANDRA_KEYSPACE_NAME");
        logger.info("Resolved 'cassandra.keyspace-name': {}", keyspace);
        if (keyspace == null || keyspace.isEmpty()) {
            keyspace = "ripple";
            logger.warn("No Cassandra keyspace configured, using default: {}", keyspace);
        }

        String localDatacenter =
                getProperty(
                        propertyLoader, "cassandra.local-datacenter", "CASSANDRA_LOCAL_DATACENTER");
        logger.info("Resolved 'cassandra.local-datacenter': {}", localDatacenter);
        if (localDatacenter == null || localDatacenter.isEmpty()) {
            localDatacenter = "datacenter1";
            logger.warn(
                    "No Cassandra local datacenter configured, using default: {}", localDatacenter);
        }

        List<String> contactPoints =
                Arrays.stream(contactPointsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        logger.info(
                "Connecting to Cassandra at {} (DC: {}, Keyspace: {})",
                contactPoints,
                localDatacenter,
                keyspace);

        CqlSession session =
                CassandraDriver.createCqlSession(contactPoints, keyspace, localDatacenter);

        return new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    private String getProperty(Function<String, String> loader, String... keys) {
        for (String key : keys) {
            try {
                String value = loader.apply(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception ignored) {
                // Ignore exceptions, means key not found or error accessing
            }
        }
        return null;
    }
}
