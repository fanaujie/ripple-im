package com.fanaujie.ripple.storage.service.impl.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoClient;
import lombok.Getter;

@Getter
public class MongoStorageFacadeBuilder {
    private MongoClient mongoClient;
    private MongoDatabase database;

    public MongoStorageFacadeBuilder mongoClient(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        return this;
    }

    public MongoStorageFacadeBuilder database(MongoDatabase database) {
        this.database = database;
        return this;
    }

    public MongoStorageFacade build() {
        return new MongoStorageFacade(this);
    }
}
