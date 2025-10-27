package com.fanaujie.ripple.communication.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ZookeeperDiscoverService {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperDiscoverService.class);
    CuratorFramework client;
    private final String serviceDiscoveryPath;
    private PathChildrenCache cache;

    public ZookeeperDiscoverService(String zookeeperAddr, String serviceDiscoveryPath)
            throws Exception {

        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        this.client = CuratorFrameworkFactory.newClient(zookeeperAddr, retryPolicy);
        this.client.start();

        this.serviceDiscoveryPath = serviceDiscoveryPath;
        Utils.createPathIfNotExists(this.client, serviceDiscoveryPath);
    }

    public void registerService(String address) throws Exception {
        client.create()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(
                        this.serviceDiscoveryPath + "/service-",
                        address.getBytes(StandardCharsets.UTF_8));

        logger.info("ZookeeperDiscoverService: Service registered: at {}", address);
    }

    public void discoverService(ServiceChangeListener listener) throws Exception {
        if (this.cache != null) {
            throw new IllegalStateException("Service discovery already started");
        }
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
        logger.info("Service discovery started");
    }

    public void close() throws IOException {
        if (cache != null) {
            cache.close();
        }

        if (client != null) {
            client.close();
        }
    }
}
