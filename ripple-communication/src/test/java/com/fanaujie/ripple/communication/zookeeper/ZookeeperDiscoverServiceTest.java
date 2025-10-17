package com.fanaujie.ripple.communication.zookeeper;
//
// import org.apache.curator.framework.CuratorFramework;
// import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
// import org.junit.jupiter.api.AfterAll;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.TestInstance;
// import org.testcontainers.containers.GenericContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
//
// import java.nio.charset.StandardCharsets;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.TimeUnit;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// @Testcontainers
// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
// public class ZookeeperDiscoverServiceTest {
//
//    private static final int ZOOKEEPER_PORT = 2181;
//    private static final String SERVICE_PATH = "/test/services";
//
//    @Container
//    static GenericContainer<?> zookeeper =
//            new GenericContainer<>("zookeeper:3.9.3-jre-17").withExposedPorts(ZOOKEEPER_PORT);
//
//    private ZookeeperDiscoverService service;
//    private String zookeeperAddr;
//
//    @BeforeAll
//    void setUp() throws Exception {
//        zookeeper.start();
//        Integer mappedPort = zookeeper.getMappedPort(ZOOKEEPER_PORT);
//        zookeeperAddr = "127.0.0.1:" + mappedPort;
//        service = new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH);
//
//        // Give Zookeeper time to initialize
//        Thread.sleep(1000);
//    }
//
//    @AfterAll
//    void tearDown() throws Exception {
//        if (service != null) {
//            service.close();
//        }
//        zookeeper.stop();
//    }
//
//    @Test
//    void testServiceRegistration() throws Exception {
//        ZookeeperDiscoverService regService =
//                new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH + "/reg");
//        String serviceAddress = "192.168.1.100:8080";
//
//        regService.registerService(serviceAddress);
//
//        // Verify the service was registered by checking if children exist
//        Thread.sleep(500);
//        List<String> children = regService.client.getChildren().forPath(SERVICE_PATH + "/reg");
//
//        assertEquals(1, children.size());
//
//        // Verify the data stored in the node
//        String nodePath = SERVICE_PATH + "/reg/" + children.get(0);
//        byte[] data = regService.client.getData().forPath(nodePath);
//        String storedAddress = new String(data, StandardCharsets.UTF_8);
//
//        assertEquals(serviceAddress, storedAddress);
//
//        System.out.println("Test 1 - Service registered: " + storedAddress);
//        regService.close();
//    }
//
//    @Test
//    void testMultipleServiceRegistrations() throws Exception {
//        ZookeeperDiscoverService multiService =
//                new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH + "/multi");
//
//        String service1 = "192.168.1.101:8081";
//        String service2 = "192.168.1.102:8082";
//        String service3 = "192.168.1.103:8083";
//
//        multiService.registerService(service1);
//        multiService.registerService(service2);
//        multiService.registerService(service3);
//
//        Thread.sleep(500);
//
//        List<String> children = multiService.client.getChildren().forPath(SERVICE_PATH +
// "/multi");
//
//        assertEquals(3, children.size());
//
//        // Verify all services are registered
//        List<String> addresses = new ArrayList<>();
//        for (String child : children) {
//            String nodePath = SERVICE_PATH + "/multi/" + child;
//            byte[] data = multiService.client.getData().forPath(nodePath);
//            addresses.add(new String(data, StandardCharsets.UTF_8));
//        }
//
//        assertTrue(addresses.contains(service1));
//        assertTrue(addresses.contains(service2));
//        assertTrue(addresses.contains(service3));
//
//        System.out.println("Test 2 - Multiple services registered: " + addresses);
//
//        multiService.close();
//    }
//
//    @Test
//    void testServiceDiscovery() throws Exception {
//        ZookeeperDiscoverService discoveryService =
//                new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH + "/discovery");
//
//        CountDownLatch latch = new CountDownLatch(2);
//        List<PathChildrenCacheEvent.Type> eventTypes = new ArrayList<>();
//
//        ServiceChangeListener listener =
//                new ServiceChangeListener() {
//                    @Override
//                    public void onServiceChanged(
//                            CuratorFramework client, PathChildrenCacheEvent event) {
//                        PathChildrenCacheEvent.Type type = event.getType();
//                        eventTypes.add(type);
//                        System.out.println("Test 3 - Event received: " + type);
//                        System.out.println("Test 3 - Path: " + event.getData().getPath());
//                        System.out.println(
//                                "Test 3 - Data: "
//                                        + new String(
//                                                event.getData().getData(),
// StandardCharsets.UTF_8));
//
//                        if (type == PathChildrenCacheEvent.Type.CHILD_ADDED) {
//                            latch.countDown();
//                        }
//                    }
//                };
//
//        discoveryService.discoverService(listener);
//
//        // Give the cache time to initialize
//        Thread.sleep(500);
//
//        // Register two services
//        discoveryService.registerService("192.168.1.201:9001");
//        discoveryService.registerService("192.168.1.202:9002");
//
//        // Wait for events
//        boolean eventsReceived = latch.await(5, TimeUnit.SECONDS);
//
//        assertTrue(eventsReceived, "Should receive CHILD_ADDED events");
//        assertTrue(
//                eventTypes.contains(PathChildrenCacheEvent.Type.CHILD_ADDED),
//                "Should contain CHILD_ADDED events");
//
//        System.out.println(
//                "Test 3 - Service discovery completed with " + eventTypes.size() + " events");
//
//        discoveryService.close();
//    }
//
//    @Test
//    void testServiceRemoval() throws Exception {
//        ZookeeperDiscoverService removalService =
//                new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH + "/removal");
//
//        CountDownLatch addLatch = new CountDownLatch(1);
//        CountDownLatch removeLatch = new CountDownLatch(1);
//
//        ServiceChangeListener listener =
//                new ServiceChangeListener() {
//                    @Override
//                    public void onServiceChanged(
//                            CuratorFramework client, PathChildrenCacheEvent event) {
//                        PathChildrenCacheEvent.Type type = event.getType();
//                        System.out.println("Test 4 - Event received: " + type);
//
//                        if (type == PathChildrenCacheEvent.Type.CHILD_ADDED) {
//                            addLatch.countDown();
//                        } else if (type == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
//                            removeLatch.countDown();
//                        }
//                    }
//                };
//
//        removalService.discoverService(listener);
//        Thread.sleep(500);
//
//        // Create a separate service that will be closed to simulate removal
//        ZookeeperDiscoverService tempService =
//                new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH + "/removal");
//        tempService.registerService("192.168.1.250:9999");
//
//        boolean addReceived = addLatch.await(3, TimeUnit.SECONDS);
//        assertTrue(addReceived, "Should receive CHILD_ADDED event");
//
//        // Close the temp service - this should trigger CHILD_REMOVED because of ephemeral node
//        tempService.close();
//
//        boolean removeReceived = removeLatch.await(3, TimeUnit.SECONDS);
//        assertTrue(removeReceived, "Should receive CHILD_REMOVED event");
//
//        System.out.println("Test 4 - Service removal detected successfully");
//
//        removalService.close();
//    }
//
//    @Test
//    void testPathCreation() throws Exception {
//        String newPath = SERVICE_PATH + "/newpath/nested";
//        ZookeeperDiscoverService pathService = new ZookeeperDiscoverService(zookeeperAddr,
// newPath);
//
//        // Verify the path was created
//        assertNotNull(pathService.client.checkExists().forPath(newPath));
//
//        System.out.println("Test 5 - Path created: " + newPath);
//
//        pathService.close();
//    }
//
//    @Test
//    void testDiscoverServiceCanOnlyBeCalledOnce() throws Exception {
//        ZookeeperDiscoverService singleDiscoveryService =
//                new ZookeeperDiscoverService(zookeeperAddr, SERVICE_PATH + "/single");
//
//        ServiceChangeListener listener =
//                new ServiceChangeListener() {
//                    @Override
//                    public void onServiceChanged(
//                            CuratorFramework client, PathChildrenCacheEvent event) {
//                        // No-op listener
//                    }
//                };
//
//        // First call should succeed
//        singleDiscoveryService.discoverService(listener);
//
//        // Second call should throw exception
//        Exception exception =
//                assertThrows(
//                        IllegalStateException.class,
//                        () -> {
//                            singleDiscoveryService.discoverService(listener);
//                        });
//
//        assertEquals("Service discovery already started", exception.getMessage());
//
//        System.out.println("Test 6 - Multiple discovery calls prevented successfully");
//
//        singleDiscoveryService.close();
//    }
// }
