package com.fanaujie.ripple.storage.driver;

import com.fanaujie.ripple.storage.service.impl.mongo.MongoStorageFacade;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoDriver {

    private static final Logger logger = LoggerFactory.getLogger(MongoDriver.class);
    private static final String DEFAULT_DATABASE_NAME = "ripple";

    public static MongoClient createMongoClient(String connectionString) {
        logger.info("=== MongoDriver.createMongoClient START ===");
        logger.info("Input connection string: {}", connectionString);

        CodecRegistry pojoCodecRegistry =
                fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry =
                fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        ConnectionString connStr = new ConnectionString(connectionString);
        MongoClientSettings.Builder settingsBuilder =
                MongoClientSettings.builder()
                        .applyConnectionString(connStr)
                        .codecRegistry(codecRegistry)
                        .applyToSocketSettings(
                                builder ->
                                        builder.connectTimeout(5, TimeUnit.SECONDS)
                                                .readTimeout(5, TimeUnit.SECONDS))
                        .applyToClusterSettings(
                                builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS));

        MongoClientSettings settings = settingsBuilder.build();
        MongoClient client = MongoClients.create(settings);
        logger.info("=== MongoDriver.createMongoClient END ===");
        return client;
    }

    public static MongoDatabase getDatabase(MongoClient mongoClient) {
        return getDatabase(mongoClient, DEFAULT_DATABASE_NAME);
    }

    public static MongoDatabase getDatabase(MongoClient mongoClient, String databaseName) {
        return mongoClient.getDatabase(databaseName);
    }
}
