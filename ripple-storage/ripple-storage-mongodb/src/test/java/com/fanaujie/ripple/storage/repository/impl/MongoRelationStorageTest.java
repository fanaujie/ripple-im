package com.fanaujie.ripple.storage.repository.impl;

import com.fanaujie.ripple.storage.driver.MongoDriver;
import com.fanaujie.ripple.storage.repository.AbstractRelationStorageTest;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.mongo.MongoStorageFacadeBuilder;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MongoRelationStorageTest extends AbstractRelationStorageTest {

    @Container static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.0");

    private MongoClient mongoClient;
    private RippleStorageFacade storageFacade;

    @BeforeEach
    void setUp() {
        mongoClient = MongoDriver.createMongoClient(mongoDBContainer.getReplicaSetUrl());
        MongoDatabase database = MongoDriver.getDatabase(mongoClient);

        storageFacade =
                new MongoStorageFacadeBuilder().mongoClient(mongoClient).database(database).build();
    }

    @AfterEach
    void tearDown() {
        if (mongoClient != null) {
            MongoDatabase database = MongoDriver.getDatabase(mongoClient);
            database.getCollection("users").drop();
            database.getCollection("user_profiles").drop();
            database.getCollection("user_relations").drop();
            database.getCollection("user_relation_versions").drop();
            database.getCollection("user_conversations").drop();
            database.getCollection("user_conversations_versions").drop();
            mongoClient.close();
        }
    }

    @Override
    protected RippleStorageFacade getStorageFacade() {
        return storageFacade;
    }
}
