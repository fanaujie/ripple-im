package com.fanaujie.ripple.uploadgateway.config;

import com.fanaujie.ripple.cache.driver.RedisDriver;
import com.fanaujie.ripple.cache.service.impl.RedisConversationSummaryStorage;
import com.fanaujie.ripple.cache.service.impl.RedisUserProfileStorage;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.communication.msgapi.impl.DefaultMessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.MessageAPIGrpc;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.spi.RippleStorageLoader;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.cache.service.UserProfileStorage;
import com.fanaujie.ripple.uploadgateway.utils.FileUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ComponentConfig {

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
    @Qualifier("avatarFileUtils")
    public FileUtils avatarFileUtils(AvatarProperties avatarProperties) {
        return new FileUtils(
                avatarProperties.getMaxFileNameLength(), avatarProperties.getMaxSize().toBytes());
    }

    @Bean
    @Qualifier("messageAttachmentFileUtils")
    public FileUtils messageAttachmentFileUtils(
            MessageAttachmentProperties messageAttachmentProperties) {
        return new FileUtils(
                messageAttachmentProperties.getMaxExtensionLength(),
                messageAttachmentProperties.getMaxFileSize().toBytes());
    }

    @Bean
    public SnowflakeIdClient snowflakeIdClient(
            @Value("${snowflakeId.server.host}") String host,
            @Value("${snowflakeId.server.port}") int port) {
        return new SnowflakeIdClient(host, port);
    }

    @Bean
    RippleStorageFacade rippleStorageFacade(Environment env) {
        return RippleStorageLoader.load(env::getProperty);
    }

    @Bean
    public RedissonClient redissonClient(
            @Value("${redis.host}") String host, @Value("${redis.port}") int port) {
        return RedisDriver.createRedissonClient(host, port);
    }

    @Bean
    public UserProfileStorage userProfileStorage(
            RedissonClient redissonClient, RippleStorageFacade storageFacade) {
        return new RedisUserProfileStorage(redissonClient, storageFacade);
    }

    @Bean
    public ConversationSummaryStorage conversationStorage(
            RedissonClient redissonClient, RippleStorageFacade storageFacade) {
        return new RedisConversationSummaryStorage(redissonClient, storageFacade);
    }
}
