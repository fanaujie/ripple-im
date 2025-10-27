package com.fanaujie.ripple.msggateway;

// import com.fanaujie.ripple.msggateway.client.WsClient;
// import com.fanaujie.ripple.msggateway.server.jwt.DefaultJwtDecoder;
// import com.fanaujie.ripple.msggateway.server.jwt.JwtDecoder;
// import com.fanaujie.ripple.msggateway.server.users.DefaultOnlineUser;
// import com.fanaujie.ripple.msggateway.server.users.OnlineUser;
// import com.fanaujie.ripple.msggateway.server.ws.WsService;
// import com.fanaujie.ripple.msggateway.server.ws.config.WsConfig;
// import com.fanaujie.ripple.protobuf.wsmessage.HeartbeatResponse;
// import org.junit.jupiter.api.*;
// import org.mockito.Mockito;
//
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.TimeUnit;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// public class SimpleWsClientServerTest {
//
//    private static final int TEST_PORT = 9090;
//    private static final String TEST_PATH = "/ws";
//    private static final String USER_ID = "123";
//    private static WsService server;
//    private static Thread serverThread;
//
//    private static final JwtDecoder jwtDecoder = Mockito.mock(DefaultJwtDecoder.class);
//
//    private static final OnlineUser onlineUser = Mockito.mock(DefaultOnlineUser.class);
//
//    @BeforeAll
//    static void startServer() throws Exception {
//        WsConfig wsConfig = new WsConfig(TEST_PORT, TEST_PATH, 60);
//        Mockito.when(jwtDecoder.decodeJwtClaims(Mockito.anyString()))
//                .thenReturn(new JwtDecoder.DecodedJwtClaims(USER_ID));
//        server = new WsService(wsConfig, jwtDecoder, onlineUser);
//        serverThread =
//                new Thread(
//                        () -> {
//                            try {
//                                server.start();
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        });
//
//        serverThread.start();
//    }
//
//    @AfterAll
//    static void stopServer() throws InterruptedException {
//        server.stop();
//        serverThread.join();
//    }
//
//    @Test
//    void testConnectSuccess() throws Exception {
//        WsClient client =
//                new WsClient("localhost", TEST_PORT, TEST_PATH, " test-token", "test-device-id");
//        try {
//            assertTrue(client.isConnected(), "Client should be connected");
//
//            CompletableFuture<HeartbeatResponse> responseFuture = client.sendHeartbeat(USER_ID);
//            HeartbeatResponse response = responseFuture.get(5, TimeUnit.SECONDS);
//
//            assertNotNull(response, "Response should not be null");
//            assertEquals(USER_ID, response.getUserId(), "User ID should match");
//            assertTrue(response.getClientTimestamp() > 0, "Client timestamp should be positive");
//            assertTrue(response.getServerTimestamp() > 0, "Server timestamp should be positive");
//            assertTrue(
//                    response.getServerTimestamp() >= response.getClientTimestamp(),
//                    "Server timestamp should be greater than or equal to client timestamp");
//
//        } finally {
//            client.close();
//        }
//    }
//
//    @Test
//    void testConnectFailure_TokenNull() {
//        try {
//            new WsClient("localhost", 9999, TEST_PATH, null, "device-id");
//            fail("Expected RuntimeException due to connection failure");
//        } catch (RuntimeException e) {
//            assertTrue(
//                    e.getMessage().contains("Failed to connect to WebSocket server"),
//                    "Exception message should indicate connection failure");
//        }
//    }
//
//    @Test
//    void testConnectFailure_DeviceIdNull() {
//        try {
//            new WsClient("localhost", 9999, TEST_PATH, "token", null);
//            fail("Expected RuntimeException due to connection failure");
//        } catch (RuntimeException e) {
//            assertTrue(
//                    e.getMessage().contains("Failed to connect to WebSocket server"),
//                    "Exception message should indicate connection failure");
//        }
//    }
// }
