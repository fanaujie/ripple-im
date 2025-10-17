package com.fanaujie.ripple.communication.zookeeper;

import org.apache.curator.framework.CuratorFramework;

public class Utils {
    public static void createPathIfNotExists(CuratorFramework client, String path)
            throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
    }
}
