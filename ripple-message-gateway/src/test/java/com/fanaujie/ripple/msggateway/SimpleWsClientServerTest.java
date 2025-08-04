package com.fanaujie.ripple.msggateway;

import com.fanaujie.ripple.msggateway.client.WsClient;
import com.fanaujie.ripple.msggateway.server.config.WsConfig;
import com.fanaujie.ripple.msggateway.server.ws.WsService;
import com.fanaujie.ripple.protobuf.messaging.HeartbeatResponse;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleWsClientServerTest {

    private static final Logger logger = LoggerFactory.getLogger(SimpleWsClientServerTest.class);

    private static final int TEST_PORT = 9090;
    private static final String TEST_PATH = "/ws";

    private static WsService server;
    private static Thread serverThread;

    @BeforeAll
    static void startServer() throws Exception {
        logger.info("Starting test server on port {}", TEST_PORT);

        WsConfig config = new WsConfig(TEST_PORT, TEST_PATH);
        server = new WsService(config);

        serverThread =
                new Thread(
                        () -> {
                            try {
                                server.start();
                            } catch (Exception e) {
                                logger.error("Server error", e);
                            }
                        });

        serverThread.start();
        Thread.sleep(3000);

        logger.info("Test server started");
    }

    @AfterAll
    static void stopServer() {
        logger.info("Stopping test server");
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    @DisplayName("Test basic WebSocket heartbeat communication")
    void testHeartbeatCommunication() throws Exception {
        logger.info("Testing basic WebSocket heartbeat communication");

        WsClient client = new WsClient("localhost", TEST_PORT, TEST_PATH);

        try {
            assertTrue(client.isConnected(), "Client should be connected");

            String userId = "test-user-123";
            logger.info("Sending heartbeat for user: {}", userId);

            CompletableFuture<HeartbeatResponse> responseFuture = client.sendHeartbeat(userId);
            HeartbeatResponse response = responseFuture.get(5, TimeUnit.SECONDS);

            logger.info("Received heartbeat response: {}", response);

            assertNotNull(response, "Response should not be null");
            assertEquals(userId, response.getUserId(), "User ID should match");
            assertTrue(response.getClientTimestamp() > 0, "Client timestamp should be positive");
            assertTrue(response.getServerTimestamp() > 0, "Server timestamp should be positive");
            assertTrue(response.getServerTimestamp() >= response.getClientTimestamp(),
                    "Server timestamp should be greater than or equal to client timestamp");

            logger.info("✅ Heartbeat protobuf encoding/decoding successful!");
            logger.info("✅ WebSocket heartbeat communication working!");

        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("Test multiple heartbeat messages")
    void testMultipleHeartbeats() throws Exception {
        logger.info("Testing multiple heartbeat messages");

        WsClient client = new WsClient("localhost", TEST_PORT, TEST_PATH);

        try {
            for (int i = 1; i <= 3; i++) {
                String userId = "test-user-" + i;

                HeartbeatResponse response = client.sendHeartbeat(userId).get(5, TimeUnit.SECONDS);

                assertEquals(userId, response.getUserId());
                assertTrue(response.getClientTimestamp() > 0);
                assertTrue(response.getServerTimestamp() > 0);

                logger.info(
                        "Heartbeat {}: userId={}, clientTimestamp={}, serverTimestamp={}",
                        i, userId, response.getClientTimestamp(), response.getServerTimestamp());

                // Small delay between heartbeats to ensure different timestamps
                Thread.sleep(10);
            }

            logger.info("✅ Multiple heartbeat messages handled correctly!");

        } finally {
            client.close();
        }
    }
}
