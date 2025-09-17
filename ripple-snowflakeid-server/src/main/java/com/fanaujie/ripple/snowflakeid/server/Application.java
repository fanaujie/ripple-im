package com.fanaujie.ripple.snowflakeid.server;

import com.fanaujie.ripple.snowflakeid.server.service.snowflakeid.SnowflakeIdService;
import com.fanaujie.ripple.snowflakeid.server.service.zookeeper.ZookeeperService;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        Config config = ConfigFactory.load();
        int port = config.getInt("server.port");
        String zookeeperAddr = config.getString("zookeeper.address");
        String zookeeperLockPath = config.getString("zookeeper.lockPath");
        String zookeeperIdsPath = config.getString("zookeeper.idsPath");
        ZookeeperService zk = null;
        try {
            zk = new ZookeeperService(zookeeperAddr, zookeeperLockPath, zookeeperIdsPath);
            int workerId = zk.acquiredWorkerId();
            if (workerId == -1) {
                logger.error("Failed to acquire a worker ID from Zookeeper.");
            } else {
                logger.info("Acquired worker ID: {}", workerId);
                SnowflakeIdService snowflakeIdService = new SnowflakeIdService(workerId, port);
                try {
                    snowflakeIdService.start();
                } catch (Exception e) {
                    logger.error("Error starting Snowflake ID service", e);
                    throw new RuntimeException(e);
                } finally {
                    snowflakeIdService.stop();
                    logger.info("Snowflake ID service stopped.");
                }
            }
        } catch (Exception e) {
            logger.error("Error with Zookeeper service", e);
        } finally {
            if (zk != null) {
                zk.close();
            }
        }
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}
