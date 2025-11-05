package com.fanaujie.ripple.apigateway.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.communication.msgapi.impl.DefaultMessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.MessageAPIGrpc;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraRelationRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraUserRepository;

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
    public UserRepository userRepository(CqlSession cqlSession) {
        return new CassandraUserRepository(cqlSession);
    }

    @Bean
    RelationRepository relationStorage(CqlSession cqlSession) {
        return new CassandraRelationRepository(cqlSession);
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
}
