package com.fanaujie.ripple.storage.repository.impl;

import com.fanaujie.ripple.storage.driver.MongoDriver;
import com.fanaujie.ripple.storage.repository.AbstractGroupStorageTest;
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
class MongoGroupStorageTest extends AbstractGroupStorageTest {

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
            database.getCollection("group_members").drop();
            database.getCollection("group_members_versions").drop();
            database.getCollection("user_groups").drop();
            database.getCollection("user_group_versions").drop();
            database.getCollection("user_conversations").drop();
            database.getCollection("user_conversations_versions").drop();
            database.getCollection("user_messages").drop();
            mongoClient.close();
        }
    }

    @Override
    protected RippleStorageFacade getStorageFacade() {
        return storageFacade;
    }
}
