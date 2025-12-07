package com.fanaujie.ripple.pushserver.service.grpc;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.zookeeper.ServiceChangeListener;
import com.fanaujie.ripple.communication.zookeeper.ZookeeperDiscoverService;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MessageGatewayClientManager implements ServiceChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(MessageGatewayClientManager.class);

    private final ZookeeperDiscoverService discoveryService;
    private final Map<String, GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> clients;

    public MessageGatewayClientManager(String zookeeperAddress, String discoveryPath)
            throws Exception {
        this.discoveryService = new ZookeeperDiscoverService(zookeeperAddress, discoveryPath);
        this.clients = new ConcurrentHashMap<>();
    }

    public void start() throws Exception {
        discoveryService.discoverService(this);
        logger.info("MessageGatewayClientManager started");
    }

    public Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> getClient(
            String serverAddress) {
        return Optional.ofNullable(clients.get(serverAddress));
    }

    @Override
    public void onServiceChanged(CuratorFramework client, PathChildrenCacheEvent event) {
        switch (event.getType()) {
            case CHILD_ADDED:
                handleServiceAdded(event);
                break;
            case CHILD_REMOVED:
                handleServiceRemoved(event);
                break;
            case CHILD_UPDATED:
                handleServiceUpdated(event);
                break;
            default:
                throw new IllegalArgumentException("Unknown event type: " + event.getType());
        }
    }

    private void handleServiceAdded(PathChildrenCacheEvent event) {
        String serverAddress = extractServerAddress(event);
        if (serverAddress != null && !clients.containsKey(serverAddress)) {
            GrpcClient<MessageGatewayGrpc.MessageGatewayStub> c =
                    new GrpcClient<>(serverAddress, MessageGatewayGrpc::newStub);
            clients.put(serverAddress, c);
            logger.info("Added MessageGateway client for server: {}", serverAddress);
        }
    }

    private void handleServiceRemoved(PathChildrenCacheEvent event) {
        String serverAddress = extractServerAddress(event);
        if (serverAddress != null) {
            var c = clients.remove(serverAddress);
            if (c != null) {
                c.getChannel().shutdown();
            }
            logger.info("Removed MessageGateway client for server: {}", serverAddress);
        }
    }

    private void handleServiceUpdated(PathChildrenCacheEvent event) {
        // not handling updates for now
    }

    private String extractServerAddress(PathChildrenCacheEvent event) {
        if (event.getData() == null || event.getData().getData() == null) {
            return null;
        }
        return new String(event.getData().getData(), StandardCharsets.UTF_8);
    }

    public void close() throws IOException {
        logger.info("Closing MessageGatewayClientManager");
        clients.forEach(
                (address, c) -> {
                    c.getChannel().shutdown();
                });
        clients.clear();
        discoveryService.close();
        logger.info("MessageGatewayClientManager closed");
    }
}
