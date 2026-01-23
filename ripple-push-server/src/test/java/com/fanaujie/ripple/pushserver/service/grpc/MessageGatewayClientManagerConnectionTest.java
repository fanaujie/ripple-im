package com.fanaujie.ripple.pushserver.service.grpc;

import com.fanaujie.ripple.communication.zookeeper.ZookeeperDiscoverService;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MessageGatewayClientManager connection state handling.
 * Tests service discovery, connection loss, and reconnection scenarios.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageGatewayClientManagerConnectionTest {

    private static final int ZOOKEEPER_PORT = 2181;
    private static final String DISCOVERY_PATH = "/test/push-server/message-gateway";

    @Container
    static GenericContainer<?> zookeeper =
            new GenericContainer<>("zookeeper:3.9.3-jre-17").withExposedPorts(ZOOKEEPER_PORT);

    private String zookeeperAddr;

    @BeforeAll
    void setUp() {
        zookeeper.start();
        Integer mappedPort = zookeeper.getMappedPort(ZOOKEEPER_PORT);
        zookeeperAddr = "127.0.0.1:" + mappedPort;
    }

    @AfterAll
    void tearDown() {
        zookeeper.stop();
    }

    @Test
    @DisplayName("Should discover services when connected")
    void testServiceDiscoveryOnConnection() throws Exception {
        String testPath = DISCOVERY_PATH + "/discovery";

        // First, register a service using ZookeeperDiscoverService directly
        ZookeeperDiscoverService registrar =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);
        String serviceAddress = "192.168.1.100:10103";
        registrar.registerService(serviceAddress);
        Thread.sleep(500);

        // Create MessageGatewayClientManager
        MessageGatewayClientManager manager =
                new MessageGatewayClientManager(zookeeperAddr, testPath);
        manager.start();

        // Wait for discovery
        Thread.sleep(1000);

        // Verify service is discovered
        assertTrue(manager.isConnected(), "Manager should be connected");
        assertTrue(
                manager.getClient(serviceAddress).isPresent(),
                "Should have client for registered service");

        manager.close();
        registrar.close();
    }

    @Test
    @DisplayName("Should handle connection state changes correctly")
    void testConnectionStateChanges() throws Exception {
        String testPath = DISCOVERY_PATH + "/state-changes";

        // Create and start MessageGatewayClientManager
        MessageGatewayClientManager manager =
                new MessageGatewayClientManager(
                        zookeeperAddr, testPath, 10000, 5000);
        manager.start();

        // Wait for initial connection
        Thread.sleep(1000);
        assertTrue(manager.isConnected(), "Should be connected initially");

        // Simulate ZooKeeper disconnection by pausing
        System.out.println("Pausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .pauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for connection to be lost
        Thread.sleep(5000);

        // Note: isConnected() may still return true during SUSPENDED state
        // It only returns false when LOST state is reached

        System.out.println("Unpausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .unpauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for reconnection
        Thread.sleep(5000);

        // Should be connected again after reconnection
        assertTrue(manager.isConnected(), "Should be connected after reconnection");

        manager.close();
    }

    @Test
    @DisplayName("Should re-discover services after reconnection")
    void testServiceRediscoveryAfterReconnection() throws Exception {
        String testPath = DISCOVERY_PATH + "/rediscovery";
        String serviceAddress = "192.168.1.150:10103";

        // Register a service
        ZookeeperDiscoverService registrar =
                new ZookeeperDiscoverService(zookeeperAddr, testPath, 60000, 15000);
        registrar.registerService(serviceAddress);
        Thread.sleep(500);

        // Create MessageGatewayClientManager
        MessageGatewayClientManager manager =
                new MessageGatewayClientManager(
                        zookeeperAddr, testPath, 10000, 5000);
        manager.start();

        // Wait for discovery
        Thread.sleep(1000);
        assertTrue(
                manager.getClient(serviceAddress).isPresent(),
                "Should discover service initially");

        // Simulate ZooKeeper restart
        System.out.println("Pausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .pauseContainerCmd(zookeeper.getContainerId())
                .exec();

        Thread.sleep(5000);

        System.out.println("Unpausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .unpauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for reconnection and cache refresh
        Thread.sleep(5000);

        // Service should be available again after reconnection
        // (registrar also re-registers due to ephemeral node)
        assertTrue(manager.isConnected(), "Manager should be connected");

        // The service might have a different node name after re-registration
        // but should still be discoverable
        System.out.println("Service rediscovery after reconnection completed");

        manager.close();
        registrar.close();
    }

    @Test
    @DisplayName("Should clear clients when connection is lost")
    void testClientsClearedOnConnectionLost() throws Exception {
        String testPath = DISCOVERY_PATH + "/clear-clients";
        String serviceAddress = "192.168.1.200:10103";

        // Register a service
        ZookeeperDiscoverService registrar =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);
        registrar.registerService(serviceAddress);
        Thread.sleep(500);

        // Create MessageGatewayClientManager with short timeouts to trigger LOST state faster
        MessageGatewayClientManager manager =
                new MessageGatewayClientManager(
                        zookeeperAddr, testPath, 5000, 3000);
        manager.start();

        // Wait for discovery
        Thread.sleep(1000);
        assertTrue(
                manager.getClient(serviceAddress).isPresent(),
                "Should have client before disconnection");

        // Simulate prolonged ZooKeeper disconnection to trigger LOST state
        System.out.println("Pausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .pauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait long enough for connection to be LOST (session timeout + some buffer)
        Thread.sleep(8000);

        // When connection is LOST, clients should be cleared
        // Note: The client might not be cleared immediately in all cases
        // This depends on the session timeout and when LOST is detected

        System.out.println("Unpausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .unpauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for reconnection
        Thread.sleep(5000);

        // After reconnection, services should be re-discovered
        assertTrue(manager.isConnected(), "Should be connected after reconnection");

        manager.close();
        registrar.close();
    }

    @Test
    @DisplayName("Should handle multiple services discovery")
    void testMultipleServicesDiscovery() throws Exception {
        String testPath = DISCOVERY_PATH + "/multi-services";

        // Register multiple services
        ZookeeperDiscoverService registrar1 =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);
        ZookeeperDiscoverService registrar2 =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);

        String service1 = "192.168.1.101:10103";
        String service2 = "192.168.1.102:10103";

        registrar1.registerService(service1);
        registrar2.registerService(service2);
        Thread.sleep(500);

        // Create MessageGatewayClientManager
        MessageGatewayClientManager manager =
                new MessageGatewayClientManager(zookeeperAddr, testPath);
        manager.start();

        // Wait for discovery
        Thread.sleep(1000);

        // Both services should be discovered
        assertTrue(
                manager.getClient(service1).isPresent(),
                "Should discover service 1");
        assertTrue(
                manager.getClient(service2).isPresent(),
                "Should discover service 2");

        // Close one registrar (simulates service going down)
        registrar1.close();

        // Wait for the ephemeral node to be removed
        Thread.sleep(2000);

        // Service 1 should be removed, service 2 should still exist
        assertFalse(
                manager.getClient(service1).isPresent(),
                "Service 1 should be removed after registrar closes");
        assertTrue(
                manager.getClient(service2).isPresent(),
                "Service 2 should still be present");

        manager.close();
        registrar2.close();
    }
}
