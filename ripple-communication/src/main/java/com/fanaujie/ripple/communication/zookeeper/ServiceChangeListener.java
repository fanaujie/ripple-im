package com.fanaujie.ripple.communication.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.state.ConnectionState;

public interface ServiceChangeListener {
    void onServiceChanged(CuratorFramework client, PathChildrenCacheEvent event);

    default void onConnectionStateChanged(CuratorFramework client, ConnectionState newState) {
        // Default implementation does nothing
    }
}
