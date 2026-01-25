package com.fanaujie.ripple.botmsgconsumer.client;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.webhookservice.WebhookDispatcherGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WebhookServiceClientManager implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(WebhookServiceClientManager.class);

    private final GrpcClient<WebhookDispatcherGrpc.WebhookDispatcherStub> client;
    private final String serverAddress;

    public WebhookServiceClientManager(String serverAddress) {
        this.serverAddress = serverAddress;
        this.client = new GrpcClient<>(serverAddress, WebhookDispatcherGrpc::newStub);
        logger.info("WebhookServiceClientManager initialized with address: {}", serverAddress);
    }

    public GrpcClient<WebhookDispatcherGrpc.WebhookDispatcherStub> getClient() {
        return client;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing WebhookServiceClientManager");
        try {
            client.getChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while closing gRPC channel");
        }
        logger.info("WebhookServiceClientManager closed");
    }
}
