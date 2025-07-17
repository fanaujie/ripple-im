package com.fanaujie.ripple.snowflakeid.server;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.snowflakeid.server.config.Config;
import com.fanaujie.ripple.snowflakeid.server.service.snowflakeid.SnowflakeIdService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SnowflakeIdServerIntegrationTest {

    private static final int ZOOKEEPER_PORT = 2181;
    private static final int SERVER_PORT = 8082;
    private static final String LOCALHOST = "127.0.0.1";

    @Container
    static GenericContainer<?> zookeeper = new GenericContainer<>("zookeeper:3.9.3-jre-17")
            .withExposedPorts(ZOOKEEPER_PORT);

    private SnowflakeIdService service;
    private SnowflakeIdClient client;

    @BeforeAll
    void setUp() throws InterruptedException {
        zookeeper.start();
        
        String zookeeperAddr = zookeeper.getHost() + ":" + zookeeper.getMappedPort(ZOOKEEPER_PORT);
        Config config = new Config(SERVER_PORT, zookeeperAddr, 1);
        service = new SnowflakeIdService(config);

        Thread serverThread = new Thread(() -> service.start());
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(2000);

        client = new SnowflakeIdClient(LOCALHOST, SERVER_PORT);
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            client.Close();
        }
        if (service != null) {
            service.stop();
        }
        zookeeper.stop();
    }

    @Test
    void testServerClientCommunication() throws Exception {
        CompletableFuture<GenerateIdResponse> future = client.requestSnowflakeId();
        
        GenerateIdResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
        assertTrue(response.getId() > 0);
        
        System.out.println("Test 1 - Received ID: " + response.getId());
        System.out.println("Test 1 - Request ID: " + response.getRequestId());
    }

    @Test
    void testMultipleRequests() throws Exception {
        CompletableFuture<GenerateIdResponse> future1 = client.requestSnowflakeId();
        CompletableFuture<GenerateIdResponse> future2 = client.requestSnowflakeId();
        
        GenerateIdResponse response1 = future1.get(5, TimeUnit.SECONDS);
        GenerateIdResponse response2 = future2.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response1);
        assertNotNull(response2);
        assertTrue(response1.getId() > 0);
        assertTrue(response2.getId() > 0);
        assertNotEquals(response1.getRequestId(), response2.getRequestId());
        
        System.out.println("Test 2 - Response 1 - ID: " + response1.getId() + ", Request ID: " + response1.getRequestId());
        System.out.println("Test 2 - Response 2 - ID: " + response2.getId() + ", Request ID: " + response2.getRequestId());
    }
}