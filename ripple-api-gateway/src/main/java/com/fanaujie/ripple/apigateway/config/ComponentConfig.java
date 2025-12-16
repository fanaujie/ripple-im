package com.fanaujie.ripple.apigateway.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.communication.msgapi.impl.DefaultMessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.MessageAPIGrpc;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.driver.CassandraDriver;

import com.fanaujie.ripple.storage.driver.RedisDriver;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.CachingConversationStorage;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraLastMessageCalculator;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUnreadCountCalculator;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.*;

@Configuration
public class ComponentConfig {

    @Bean
    public CqlSession cqlSession(
            @Value("${cassandra.contact-points}") List<String> contactPoints,
            @Value("${cassandra.keyspace-name}") String keyspace,
            @Value("${cassandra.local-datacenter}") String localDatacenter) {
        return CassandraDriver.createCqlSession(contactPoints, keyspace, localDatacenter);
    }

    @Bean
    RippleStorageFacade userStorageAggregator(CqlSession cqlSession) {
        CassandraStorageFacadeBuilder b = new CassandraStorageFacadeBuilder();
        b.cqlSession(cqlSession);
        return b.build();
    }

    @Bean
    public ExecutorService executorService(
            @Value("${ripple.executor.queue.capacity}") int queueCapacity) {
        int cpuCoreSize = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cpuCoreSize,
                cpuCoreSize * 2,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity));
    }

    @Bean
    public GrpcClient<MessageAPIGrpc.MessageAPIBlockingStub> messageDispatcherClient(
            @Value("${message-api.server.address}") String serverAddress) {
        return new GrpcClient<>(serverAddress, MessageAPIGrpc::newBlockingStub);
    }

    @Bean
    public MessageAPISender messageAPISender(
            ExecutorService executorService,
            GrpcClient<MessageAPIGrpc.MessageAPIBlockingStub> messageDispatcherClient) {
        return new DefaultMessageAPISender(executorService, messageDispatcherClient);
    }

    @Bean
    public SnowflakeIdClient snowflakeIdClient(
            @Value("${snowflakeId.server.host}") String host,
            @Value("${snowflakeId.server.port}") int port) {
        return new SnowflakeIdClient(host, port);
    }

    @Bean
    public RedissonClient redissonClient(
            @Value("${redis.host}") String host, @Value("${redis.port}") int port) {
        return RedisDriver.createRedissonClient(host, port);
    }

    @Bean
    public CassandraUnreadCountCalculator cassandraUnreadCountCalculator(CqlSession cqlSession) {
        return new CassandraUnreadCountCalculator(cqlSession);
    }

    @Bean
    public CassandraLastMessageCalculator cassandraLastMessageCalculator(CqlSession cqlSession) {
        return new CassandraLastMessageCalculator(cqlSession);
    }

    @Bean
    public ConversationStateFacade conversationStorage(
            RedissonClient redissonClient,
            CassandraUnreadCountCalculator cassandraUnreadCountCalculator,
            CassandraLastMessageCalculator cassandraLastMessageCalculator) {
        return new CachingConversationStorage(
                redissonClient, cassandraUnreadCountCalculator, cassandraLastMessageCalculator);
    }
}
