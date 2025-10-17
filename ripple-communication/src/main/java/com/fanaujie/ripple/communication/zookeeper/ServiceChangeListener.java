package com.fanaujie.ripple.communication.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

public interface ServiceChangeListener {
    void onServiceChanged(CuratorFramework client, PathChildrenCacheEvent event);
}
