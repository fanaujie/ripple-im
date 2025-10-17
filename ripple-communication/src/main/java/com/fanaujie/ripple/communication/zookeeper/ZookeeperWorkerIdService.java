package com.fanaujie.ripple.communication.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

public class ZookeeperWorkerIdService {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperWorkerIdService.class);
    private static final int MAX_WORKER_ID = 1024;
    private CuratorFramework client;
    private String lockPath;
    private String idsPath;

    public ZookeeperWorkerIdService(String zookeeperAddr, String lockPath, String idsPath)
            throws Exception {
        this.lockPath = lockPath;
        this.idsPath = idsPath;
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        this.client = CuratorFrameworkFactory.newClient(zookeeperAddr, retryPolicy);
        this.client.start();
        this.createPathIfNotExists(this.lockPath);
        this.createPathIfNotExists(this.idsPath);
    }

    public int acquiredWorkerId() throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);
        try {
            lock.acquire();
            Map<Integer, Boolean> idMap =
                    this.client.getChildren().forPath(this.idsPath).stream()
                            .collect(Collectors.toMap(Integer::parseInt, id -> false));
            for (int i = 0; i < MAX_WORKER_ID; i++) {
                if (!idMap.containsKey(i)) {
                    String nodePath = String.format("%s/%d", idsPath, i);
                    this.client.create().withMode(CreateMode.EPHEMERAL).forPath(nodePath);
                    return i;
                }
            }
            return -1;
        } finally {
            lock.release();
        }
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }

    private void createPathIfNotExists(String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
    }
}
