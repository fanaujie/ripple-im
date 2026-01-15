package com.fanaujie.ripple.integration.message;

import com.fanaujie.ripple.storage.driver.MongoDriver;
import com.fanaujie.ripple.storage.service.impl.mongo.MongoStorageFacadeBuilder;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MongoMessageFlowTest extends AbstractMessageFlowTest {

    @Container
    protected static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    protected MongoClient mongoClient;
    protected MongoDatabase database;

    @Override
    protected void initializeStorage() {
        this.mongoClient = MongoDriver.createMongoClient(mongoDBContainer.getReplicaSetUrl());
        this.database = MongoDriver.getDatabase(mongoClient, "ripple");
        this.storageFacade = new MongoStorageFacadeBuilder()
                .mongoClient(mongoClient)
                .database(database)
                .build();
    }

    @Override
    protected void cleanupStorage() {
        if (database != null) {
            database.drop();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
