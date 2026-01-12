package com.fanaujie.ripple.storage.driver;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.concurrent.TimeUnit;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoDriver {

    private static final String DEFAULT_DATABASE_NAME = "ripple";

    public static MongoClient createMongoClient(String connectionString) {
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .codecRegistry(codecRegistry)
                .applyToSocketSettings(builder -> builder.connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build();

        return MongoClients.create(settings);
    }

    public static MongoDatabase getDatabase(MongoClient mongoClient) {
        return getDatabase(mongoClient, DEFAULT_DATABASE_NAME);
    }

    public static MongoDatabase getDatabase(MongoClient mongoClient, String databaseName) {
        return mongoClient.getDatabase(databaseName);
    }
}
