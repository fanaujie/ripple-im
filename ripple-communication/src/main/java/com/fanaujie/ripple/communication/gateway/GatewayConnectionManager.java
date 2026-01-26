package com.fanaujie.ripple.communication.gateway;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.zookeeper.ServiceChangeListener;
import com.fanaujie.ripple.communication.zookeeper.ZookeeperDiscoverService;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GatewayConnectionManager implements ServiceChangeListener, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(GatewayConnectionManager.class);

    private final ZookeeperDiscoverService discoveryService;
    private final Map<String, GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> clients;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public GatewayConnectionManager(String zookeeperAddress, String discoveryPath)
            throws Exception {
        this.discoveryService = new ZookeeperDiscoverService(zookeeperAddress, discoveryPath);
        this.clients = new ConcurrentHashMap<>();
    }

    public GatewayConnectionManager(
            String zookeeperAddress,
            String discoveryPath,
            int sessionTimeoutMs,
            int connectionTimeoutMs)
            throws Exception {
        this.discoveryService =
                new ZookeeperDiscoverService(
                        zookeeperAddress, discoveryPath, sessionTimeoutMs, connectionTimeoutMs);
        this.clients = new ConcurrentHashMap<>();
    }

    public void start() throws Exception {
        discoveryService.discoverService(this);
        connected.set(true);
        logger.info("GatewayConnectionManager started");
    }

    public Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> getClient(
            String serverAddress) {
        return Optional.ofNullable(clients.get(serverAddress));
    }

    public boolean isConnected() {
        return connected.get();
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
                // Not handling updates for now
                break;
            default:
                logger.warn("Unknown event type: {}", event.getType());
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

    @Override
    public void onConnectionStateChanged(CuratorFramework client, ConnectionState newState) {
        switch (newState) {
            case CONNECTED:
            case RECONNECTED:
                connected.set(true);
                logger.info(
                        "ZooKeeper connection {} - GatewayConnectionManager is ready",
                        newState == ConnectionState.CONNECTED ? "established" : "restored");
                break;
            case SUSPENDED:
                logger.warn(
                        "ZooKeeper connection SUSPENDED - push operations may fail until connection is restored");
                break;
            case LOST:
                connected.set(false);
                logger.error(
                        "ZooKeeper connection LOST - clearing cached clients and waiting for reconnection");
                clearAllClients();
                break;
            case READ_ONLY:
                logger.warn("ZooKeeper connection is READ_ONLY");
                break;
        }
    }

    private void clearAllClients() {
        clients.forEach(
                (address, c) -> {
                    try {
                        c.getChannel().shutdown();
                    } catch (Exception e) {
                        logger.warn(
                                "Error shutting down client for {}: {}", address, e.getMessage());
                    }
                });
        clients.clear();
        logger.info("All cached MessageGateway clients cleared");
    }

    private String extractServerAddress(PathChildrenCacheEvent event) {
        if (event.getData() == null || event.getData().getData() == null) {
            return null;
        }
        return new String(event.getData().getData(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing GatewayConnectionManager");
        clients.forEach((address, c) -> c.getChannel().shutdown());
        clients.clear();
        discoveryService.close();
        logger.info("GatewayConnectionManager closed");
    }
}
