package com.fanaujie.ripple.storage.mongodb.spi;

import com.fanaujie.ripple.storage.driver.MongoDriver;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.mongo.MongoStorageFacadeBuilder;
import com.fanaujie.ripple.storage.spi.RippleStorageProvider;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class MongoStorageProvider implements RippleStorageProvider {
    private static final Logger logger = LoggerFactory.getLogger(MongoStorageProvider.class);

    @Override
    public RippleStorageFacade create(Function<String, String> propertyLoader) {
        logger.info("Initializing MongoStorageProvider...");

        String uri = getProperty(propertyLoader, "mongodb.uri", "MONGODB_URI");
        logger.info("Resolved 'mongodb.uri': {}", uri);
        if (uri == null || uri.isEmpty()) {
            uri = "mongodb://localhost:27017";
            logger.warn("No MongoDB URI configured, using default: {}", uri);
        }

        String dbName = getProperty(propertyLoader, "mongodb.database", "MONGODB_DATABASE");
        logger.info("Resolved 'mongodb.database': {}", dbName);
        if (dbName == null || dbName.isEmpty()) {
            dbName = "ripple";
            logger.warn("No MongoDB database name configured, using default: {}", dbName);
        }

        MongoClient mongoClient = MongoDriver.createMongoClient(uri);
        MongoDatabase database = MongoDriver.getDatabase(mongoClient, dbName);

        return new MongoStorageFacadeBuilder()
                .mongoClient(mongoClient)
                .database(database)
                .build();
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
