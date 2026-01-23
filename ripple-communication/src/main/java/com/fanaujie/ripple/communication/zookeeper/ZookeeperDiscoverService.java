package com.fanaujie.ripple.communication.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZookeeperDiscoverService {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperDiscoverService.class);

    private static final int DEFAULT_SESSION_TIMEOUT_MS = 60000;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 15000;
    private static final int DEFAULT_BASE_SLEEP_TIME_MS = 1000;
    private static final int DEFAULT_MAX_RETRIES =
            29; // Will retry for about 5 minutes with exponential backoff
    private static final int RECONNECT_RECOVERY_MAX_RETRIES = 3;

    CuratorFramework client;
    private final String serviceDiscoveryPath;
    private PathChildrenCache cache;
    private ServiceChangeListener serviceChangeListener;
    private String registeredServiceAddress; // Store for re-registration on reconnect
    private final ExecutorService recoveryExecutor;
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);

    public ZookeeperDiscoverService(String zookeeperAddr, String serviceDiscoveryPath)
            throws Exception {
        this(
                zookeeperAddr,
                serviceDiscoveryPath,
                DEFAULT_SESSION_TIMEOUT_MS,
                DEFAULT_CONNECTION_TIMEOUT_MS);
    }

    public ZookeeperDiscoverService(
            String zookeeperAddr,
            String serviceDiscoveryPath,
            int sessionTimeoutMs,
            int connectionTimeoutMs)
            throws Exception {

        this.recoveryExecutor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "zk-recovery-executor");
                            t.setDaemon(true);
                            return t;
                        });

        RetryPolicy retryPolicy =
                new ExponentialBackoffRetry(DEFAULT_BASE_SLEEP_TIME_MS, DEFAULT_MAX_RETRIES);
        this.client =
                CuratorFrameworkFactory.builder()
                        .connectString(zookeeperAddr)
                        .sessionTimeoutMs(sessionTimeoutMs)
                        .connectionTimeoutMs(connectionTimeoutMs)
                        .retryPolicy(retryPolicy)
                        .build();

        this.client
                .getConnectionStateListenable()
                .addListener(
                        new ConnectionStateListener() {
                            @Override
                            public void stateChanged(
                                    CuratorFramework client, ConnectionState newState) {
                                handleConnectionStateChange(newState);
                            }
                        });

        this.client.start();

        this.serviceDiscoveryPath = serviceDiscoveryPath;
        Utils.createPathIfNotExists(this.client, serviceDiscoveryPath);
    }

    private void handleConnectionStateChange(ConnectionState newState) {
        logger.info("ZooKeeper connection state changed to: {}", newState);

        switch (newState) {
            case CONNECTED:
                logger.info("ZooKeeper connected");
                break;
            case RECONNECTED:
                logger.info("ZooKeeper reconnected, scheduling recovery tasks...");
                scheduleRecoveryTasks();
                break;
            case SUSPENDED:
                logger.warn("ZooKeeper connection SUSPENDED - operations may fail");
                break;
            case LOST:
                logger.error("ZooKeeper connection LOST - will attempt to reconnect");
                break;
            case READ_ONLY:
                logger.warn("ZooKeeper connection is READ_ONLY");
                break;
        }

        if (serviceChangeListener != null) {
            serviceChangeListener.onConnectionStateChanged(client, newState);
        }
    }

    private void scheduleRecoveryTasks() {
        if (!recoveryInProgress.compareAndSet(false, true)) {
            logger.info("Recovery already in progress, skipping duplicate request");
            return;
        }

        recoveryExecutor.submit(
                () -> {
                    try {
                        reRegisterService();
                        refreshServiceCache();
                        logger.info("Recovery tasks completed successfully");
                    } catch (Exception e) {
                        logger.error("Unexpected error during recovery tasks", e);
                    } finally {
                        recoveryInProgress.set(false);
                    }
                });
    }

    private void reRegisterService() {
        try {
            client.create()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(
                            this.serviceDiscoveryPath + "/service-",
                            registeredServiceAddress.getBytes(StandardCharsets.UTF_8));

            logger.info("Service re-registered successfully at {}", registeredServiceAddress);
        } catch (Exception e) {
            logger.warn("Failed to re-register service: {}", e.getMessage());
        }
    }

    private void refreshServiceCache() {
        try {
            cache.rebuild();
            logger.info("Service cache rebuilt successfully");
            for (ChildData data : cache.getCurrentData()) {
                PathChildrenCacheEvent event =
                        new PathChildrenCacheEvent(PathChildrenCacheEvent.Type.CHILD_ADDED, data);
                serviceChangeListener.onServiceChanged(client, event);
            }
            logger.info("Re-notified listener about {} services", cache.getCurrentData().size());
        } catch (Exception e) {
            logger.error(
                    "Failed to refresh service cache after {} attempts",
                    RECONNECT_RECOVERY_MAX_RETRIES,
                    e);
        }
    }

    public void registerService(String address) throws Exception {
        // Store the address for re-registration on reconnect
        this.registeredServiceAddress = address;

        client.create()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(
                        this.serviceDiscoveryPath + "/service-",
                        address.getBytes(StandardCharsets.UTF_8));

        logger.info("ZookeeperDiscoverService: Service registered at {}", address);
    }

    public void discoverService(ServiceChangeListener listener) throws Exception {
        if (this.cache != null) {
            throw new IllegalStateException("Service discovery already started");
        }

        // Store the listener reference for connection state change notifications
        this.serviceChangeListener = listener;

        this.cache = new PathChildrenCache(client, this.serviceDiscoveryPath, true);
        this.cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        for (ChildData data : this.cache.getCurrentData()) {
            PathChildrenCacheEvent event =
                    new PathChildrenCacheEvent(PathChildrenCacheEvent.Type.CHILD_ADDED, data);
            listener.onServiceChanged(client, event);
        }
        PathChildrenCacheListener pathListener =
                new PathChildrenCacheListener() {
                    @Override
                    public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) {
                        listener.onServiceChanged(client, event);
                    }
                };
        cache.getListenable().addListener(pathListener);
        logger.info("Service discovery started with connection state monitoring");
    }

    public void close() throws IOException {
        // Shutdown recovery executor
        recoveryExecutor.shutdown();
        try {
            if (!recoveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                recoveryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            recoveryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (cache != null) {
            cache.close();
        }

        if (client != null) {
            client.close();
        }
    }

    public CuratorFramework getClient() {
        return client;
    }
}
