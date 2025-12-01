package com.fanaujie.ripple.userpresence;

import com.fanaujie.ripple.storage.driver.RedisDriver;
import com.fanaujie.ripple.storage.service.UserPresenceStorage;
import com.fanaujie.ripple.storage.service.impl.redis.RedisUserPresenceStorage;
import com.fanaujie.ripple.userpresence.server.GrpcServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        Config config = ConfigFactory.load();
        int grpcPort = config.getInt("server.grpc.port");
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        int userPresenceTtlSeconds = config.getInt("user-presence.ttl-seconds");
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Starting User Presence server...");
        logger.info("gRPC Port: {}", grpcPort);
        logger.info("User Presence TTL (seconds): {}", userPresenceTtlSeconds);

        UserPresenceStorage userPresenceStorage =
                new RedisUserPresenceStorage(
                        RedisDriver.createRedissonClient(redisHost, redisPort),
                        userPresenceTtlSeconds);
        GrpcServer grpcServer = new GrpcServer(grpcPort, userPresenceStorage);
        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        grpcFuture.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}
