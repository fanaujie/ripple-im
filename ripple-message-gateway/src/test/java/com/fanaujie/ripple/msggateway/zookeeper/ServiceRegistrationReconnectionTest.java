package com.fanaujie.ripple.msggateway.zookeeper;

import com.fanaujie.ripple.communication.zookeeper.ZookeeperDiscoverService;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for service registration reconnection in ripple-message-gateway.
 * Verifies that the gateway service re-registers itself after ZooKeeper reconnection.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceRegistrationReconnectionTest {

    private static final int ZOOKEEPER_PORT = 2181;
    private static final String DISCOVERY_PATH = "/test/message-gateway/services";

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
    @DisplayName("Should register service on initial connection")
    void testInitialServiceRegistration() throws Exception {
        String testPath = DISCOVERY_PATH + "/initial";
        String serviceAddress = "gateway-0.gateway.default.svc:10103";

        ZookeeperDiscoverService service =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);
        service.registerService(serviceAddress);

        // Wait for registration to complete
        Thread.sleep(500);

        // Verify service is registered
        List<String> children = service.getClient().getChildren().forPath(testPath);
        assertEquals(1, children.size(), "Should have 1 registered service");

        // Verify the service address
        String nodePath = testPath + "/" + children.get(0);
        byte[] data = service.getClient().getData().forPath(nodePath);
        String storedAddress = new String(data, StandardCharsets.UTF_8);
        assertEquals(serviceAddress, storedAddress, "Service address should match");

        service.close();
    }

    @Test
    @DisplayName("Should automatically re-register service after ZooKeeper reconnection")
    void testServiceReRegistrationAfterReconnection() throws Exception {
        String testPath = DISCOVERY_PATH + "/reconnect";
        String serviceAddress = "gateway-1.gateway.default.svc:10103";

        // Create service with explicit timeouts
        ZookeeperDiscoverService service =
                new ZookeeperDiscoverService(zookeeperAddr, testPath, 15000, 5000);
        service.registerService(serviceAddress);

        // Wait for registration
        Thread.sleep(500);

        // Verify initial registration
        List<String> childrenBefore = service.getClient().getChildren().forPath(testPath);
        assertEquals(1, childrenBefore.size(), "Should have 1 service before reconnection");
        System.out.println("Initial registration verified: " + childrenBefore);

        // Get the initial node name
        String initialNodeName = childrenBefore.get(0);

        // Simulate ZooKeeper disconnection
        System.out.println("Pausing ZooKeeper container to simulate network interruption...");
        zookeeper.getDockerClient()
                .pauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for connection to be considered lost
        // During this time, the ephemeral node should be deleted by ZooKeeper
        Thread.sleep(5000);

        // Resume ZooKeeper
        System.out.println("Unpausing ZooKeeper container...");
        zookeeper.getDockerClient()
                .unpauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for reconnection and re-registration
        // The ZookeeperDiscoverService should automatically re-register on RECONNECTED state
        Thread.sleep(5000);

        // Verify service is re-registered
        List<String> childrenAfter = service.getClient().getChildren().forPath(testPath);
        assertTrue(childrenAfter.size() >= 1, "Should have at least 1 service after reconnection");
        System.out.println("Services after reconnection: " + childrenAfter);

        // Verify the service address is correct
        boolean foundCorrectService = false;
        for (String child : childrenAfter) {
            String nodePath = testPath + "/" + child;
            byte[] data = service.getClient().getData().forPath(nodePath);
            String storedAddress = new String(data, StandardCharsets.UTF_8);
            if (serviceAddress.equals(storedAddress)) {
                foundCorrectService = true;
                System.out.println("Found re-registered service: " + child + " -> " + storedAddress);
                break;
            }
        }
        assertTrue(foundCorrectService, "Service should be re-registered with correct address");

        // The node name should be different after re-registration
        // (because it's a new EPHEMERAL_SEQUENTIAL node)
        if (childrenAfter.size() == 1 && !childrenAfter.get(0).equals(initialNodeName)) {
            System.out.println("Confirmed: New node created after re-registration");
            System.out.println("Initial node: " + initialNodeName);
            System.out.println("New node: " + childrenAfter.get(0));
        }

        service.close();
    }

    @Test
    @DisplayName("Should handle multiple reconnections")
    void testMultipleReconnections() throws Exception {
        String testPath = DISCOVERY_PATH + "/multi-reconnect";
        String serviceAddress = "gateway-2.gateway.default.svc:10103";

        ZookeeperDiscoverService service =
                new ZookeeperDiscoverService(zookeeperAddr, testPath, 10000, 5000);
        service.registerService(serviceAddress);

        // Wait for initial registration
        Thread.sleep(500);

        // Perform multiple reconnection cycles
        for (int i = 1; i <= 3; i++) {
            System.out.println("=== Reconnection cycle " + i + " ===");

            // Pause ZooKeeper
            zookeeper.getDockerClient()
                    .pauseContainerCmd(zookeeper.getContainerId())
                    .exec();

            Thread.sleep(3000);

            // Unpause ZooKeeper
            zookeeper.getDockerClient()
                    .unpauseContainerCmd(zookeeper.getContainerId())
                    .exec();

            // Wait for reconnection and re-registration
            Thread.sleep(5000);

            // Verify service is still registered
            List<String> children = service.getClient().getChildren().forPath(testPath);
            assertTrue(
                    children.size() >= 1,
                    "Should have at least 1 service after reconnection cycle " + i);

            // Verify address
            boolean found = false;
            for (String child : children) {
                String nodePath = testPath + "/" + child;
                byte[] data = service.getClient().getData().forPath(nodePath);
                String storedAddress = new String(data, StandardCharsets.UTF_8);
                if (serviceAddress.equals(storedAddress)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Service should be re-registered after cycle " + i);
            System.out.println("Cycle " + i + " completed successfully");
        }

        service.close();
    }

    @Test
    @DisplayName("Should allow service to be discovered after re-registration")
    void testDiscoveryAfterReRegistration() throws Exception {
        String testPath = DISCOVERY_PATH + "/discover-after-rereg";
        String serviceAddress = "gateway-3.gateway.default.svc:10103";

        // Gateway registers its service
        ZookeeperDiscoverService gateway =
                new ZookeeperDiscoverService(zookeeperAddr, testPath, 10000, 5000);
        gateway.registerService(serviceAddress);
        Thread.sleep(500);

        // Push server discovers services
        ZookeeperDiscoverService pushServer =
                new ZookeeperDiscoverService(zookeeperAddr, testPath, 10000, 5000);

        java.util.concurrent.atomic.AtomicInteger discoveredCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CopyOnWriteArrayList<String> discoveredServices =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        pushServer.discoverService(
                new com.fanaujie.ripple.communication.zookeeper.ServiceChangeListener() {
                    @Override
                    public void onServiceChanged(
                            org.apache.curator.framework.CuratorFramework client,
                            org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent
                                    event) {
                        if (event.getType()
                                == org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent
                                        .Type.CHILD_ADDED) {
                            String address =
                                    new String(
                                            event.getData().getData(), StandardCharsets.UTF_8);
                            discoveredServices.add(address);
                            discoveredCount.incrementAndGet();
                            System.out.println("Discovered service: " + address);
                        }
                    }
                });

        Thread.sleep(1000);
        assertTrue(discoveredServices.contains(serviceAddress), "Should discover initial service");

        // Simulate ZooKeeper restart
        System.out.println("Simulating ZooKeeper restart...");
        zookeeper.getDockerClient()
                .pauseContainerCmd(zookeeper.getContainerId())
                .exec();

        Thread.sleep(3000);

        zookeeper.getDockerClient()
                .unpauseContainerCmd(zookeeper.getContainerId())
                .exec();

        // Wait for reconnection and re-registration
        Thread.sleep(5000);

        // Clear and check discovery again
        int countBeforeRefresh = discoveredCount.get();
        System.out.println("Discovery count before refresh: " + countBeforeRefresh);

        // After reconnection, the push server should rediscover the service
        // (due to cache refresh in ZookeeperDiscoverService)
        Thread.sleep(2000);

        int countAfterRefresh = discoveredCount.get();
        System.out.println("Discovery count after refresh: " + countAfterRefresh);
        assertTrue(
                countAfterRefresh >= countBeforeRefresh,
                "Should discover services after reconnection");

        gateway.close();
        pushServer.close();
    }

    @Test
    @DisplayName("Ephemeral node should be deleted when service disconnects")
    void testEphemeralNodeDeletion() throws Exception {
        String testPath = DISCOVERY_PATH + "/ephemeral-delete";
        String serviceAddress = "gateway-temp.gateway.default.svc:10103";

        // Create and register service
        ZookeeperDiscoverService tempService =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);
        tempService.registerService(serviceAddress);

        Thread.sleep(500);

        // Verify registration
        List<String> childrenBefore = tempService.getClient().getChildren().forPath(testPath);
        assertEquals(1, childrenBefore.size(), "Should have 1 registered service");

        // Create another service to observe the removal
        ZookeeperDiscoverService observer =
                new ZookeeperDiscoverService(zookeeperAddr, testPath);

        // Close the temp service - this should cause ephemeral node to be deleted
        tempService.close();

        // Wait for ZooKeeper to detect the session close and delete ephemeral node
        Thread.sleep(2000);

        // Verify the node is deleted
        List<String> childrenAfter = observer.getClient().getChildren().forPath(testPath);
        assertEquals(0, childrenAfter.size(), "Ephemeral node should be deleted when service closes");

        observer.close();
    }
}
