package com.fanaujie.ripple.pushserver.service.grpc;

import com.fanaujie.ripple.communication.grpc.client.GrpcClientPool;
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

public class MessageGatewayClientPoolManager implements ServiceChangeListener {
    private static final Logger logger =
            LoggerFactory.getLogger(MessageGatewayClientPoolManager.class);

    private final ZookeeperDiscoverService discoveryService;
    private final Map<String, GrpcClientPool<MessageGatewayGrpc.MessageGatewayStub>> clientPools;

    public MessageGatewayClientPoolManager(String zookeeperAddress, String discoveryPath)
            throws Exception {
        this.discoveryService = new ZookeeperDiscoverService(zookeeperAddress, discoveryPath);
        this.clientPools = new ConcurrentHashMap<>();
    }

    public void start() throws Exception {
        discoveryService.discoverService(this);
        logger.info("MessageGatewayClientPoolManager started");
    }

    public Optional<GrpcClientPool<MessageGatewayGrpc.MessageGatewayStub>> getClientPool(
            String serverAddress) {
        return Optional.ofNullable(clientPools.get(serverAddress));
    }

    @Override
    public void onServiceChanged(CuratorFramework client, PathChildrenCacheEvent event) {
        try {
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
                    logger.debug("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling service change event", e);
        }
    }

    private void handleServiceAdded(PathChildrenCacheEvent event) {
        String serverAddress = extractServerAddress(event);
        if (serverAddress != null && !clientPools.containsKey(serverAddress)) {
            GrpcClientPool<MessageGatewayGrpc.MessageGatewayStub> pool =
                    new GrpcClientPool<>(serverAddress, MessageGatewayGrpc::newStub);
            clientPools.put(serverAddress, pool);
            logger.info("Added MessageGateway client pool for server: {}", serverAddress);
        }
    }

    private void handleServiceRemoved(PathChildrenCacheEvent event) {
        String serverAddress = extractServerAddress(event);
        if (serverAddress != null) {
            GrpcClientPool<MessageGatewayGrpc.MessageGatewayStub> pool =
                    clientPools.remove(serverAddress);
            if (pool != null) {
                pool.close();
                logger.info("Removed MessageGateway client pool for server: {}", serverAddress);
            }
        }
    }

    private void handleServiceUpdated(PathChildrenCacheEvent event) {
        String serverAddress = extractServerAddress(event);
        logger.debug("Service updated: {}", serverAddress);
        // For updates, we might want to recreate the pool if the address changed
        // For now, just log it
    }

    private String extractServerAddress(PathChildrenCacheEvent event) {
        if (event.getData() == null || event.getData().getData() == null) {
            return null;
        }
        return new String(event.getData().getData(), StandardCharsets.UTF_8);
    }

    public void close() throws IOException {
        logger.info("Closing MessageGatewayClientPoolManager");

        // Close all client pools
        clientPools.forEach(
                (address, pool) -> {
                    try {
                        pool.close();
                        logger.debug("Closed client pool for: {}", address);
                    } catch (Exception e) {
                        logger.error("Error closing client pool for: {}", address, e);
                    }
                });
        clientPools.clear();

        // Close Zookeeper connection
        discoveryService.close();
        logger.info("MessageGatewayClientPoolManager closed");
    }
}
