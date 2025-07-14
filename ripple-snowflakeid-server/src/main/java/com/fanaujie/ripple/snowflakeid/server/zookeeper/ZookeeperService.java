package com.fanaujie.ripple.snowflakeid.server.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.util.Map;
import java.util.stream.Collectors;


public class ZookeeperService {

    private CuratorFramework client;
    private String lockPath;
    private String idsPath;

    public ZookeeperService(String zookeeperAddr,String lockPath,String idsPath) throws Exception {
        this.lockPath = lockPath;
        this.idsPath = idsPath;
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        this.client = CuratorFrameworkFactory.newClient(zookeeperAddr,retryPolicy);
        this.client.start();
        this.createPathIfNotExists(this.lockPath);
        this.createPathIfNotExists(this.idsPath);
    }

    public int acquiredWorkerId() throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);
        try {
            lock.acquire();
            Map<Integer,Boolean> idMap = this.client.getChildren().forPath(this.idsPath).stream().
                    collect(Collectors.toMap(
                            Integer::parseInt,
                            id -> false
                    ));
            for (int i = 0; i < 1024; i++) {
                if (!idMap.containsKey(i)) {
                    this.client.create().withMode(CreateMode.EPHEMERAL)
                            .forPath(String.format("%s/%d", idsPath, i));
                    return i;
                }
            }
        } finally {
            lock.release();
        }
        return 0;
    }

    private void createPathIfNotExists(String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create()
                    .creatingParentsIfNeeded()
                    .forPath(path);
        }
    }
}
