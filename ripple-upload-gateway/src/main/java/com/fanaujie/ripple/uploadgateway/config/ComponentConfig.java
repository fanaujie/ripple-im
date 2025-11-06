package com.fanaujie.ripple.uploadgateway.config;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.communication.msgapi.impl.DefaultMessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.MessageAPIGrpc;
import com.fanaujie.ripple.uploadgateway.utils.FileUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
