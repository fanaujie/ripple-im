package com.fanaujie.ripple.userpresence.server;

import com.fanaujie.ripple.communication.grpc.client.GrpcClientPool;
import com.fanaujie.ripple.protobuf.userpresence.*;
import com.fanaujie.ripple.storage.service.UserPresenceStorage;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrpcServerTest {

    private GrpcServer grpcServer;
    private GrpcClientPool<UserPresenceGrpc.UserPresenceBlockingStub> clientPool;
    private UserPresenceStorage mockStorageService;
    private int serverPort;

    @BeforeEach
    void setUp() throws Exception {
        serverPort = findAvailablePort();

        mockStorageService = mock(UserPresenceStorage.class);

        grpcServer = new GrpcServer(serverPort, mockStorageService);
        grpcServer.startAsync();

        Thread.sleep(1000);

        String serverAddr = "localhost:" + serverPort;
        clientPool = new GrpcClientPool<>(serverAddr, UserPresenceGrpc::newBlockingStub);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (clientPool != null) {
            clientPool.close();
        }

        if (grpcServer != null) {
            grpcServer.stop();
        }
    }

    @Test
    void testSetUserOnline() throws Exception {
        UserOnlineReq request =
                UserOnlineReq.newBuilder()
                        .setUserId(12345L)
                        .setDeviceId("device-001")
                        .setIsOnline(true)
                        .setServerLocation("server-01")
                        .build();

        clientPool.execute(
                stub -> {
                    UserOnlineResp response = stub.setUserOnline(request);
                    assertNotNull(response);
                });

        ArgumentCaptor<UserOnlineReq> captor = ArgumentCaptor.forClass(UserOnlineReq.class);
        verify(mockStorageService, times(1)).setUserOnline(captor.capture());

        UserOnlineReq capturedRequest = captor.getValue();
        assertEquals(12345L, capturedRequest.getUserId());
        assertEquals("device-001", capturedRequest.getDeviceId());
        assertTrue(capturedRequest.getIsOnline());
        assertEquals("server-01", capturedRequest.getServerLocation());
    }

    @Test
    void testQueryUserOnline() throws Exception {
        QueryUserOnlineReq request =
                QueryUserOnlineReq.newBuilder()
                        .addAllUserIds(Arrays.asList(100L, 200L, 300L))
                        .build();
        UserOnlineInfo info100L =
                UserOnlineInfo.newBuilder().setUserId(100L).setServerLocation("loc1").build();
        UserOnlineInfo info300L =
                UserOnlineInfo.newBuilder().setUserId(300L).setServerLocation("loc3").build();
        UserOnlineInfo info200L =
                UserOnlineInfo.newBuilder().setUserId(200L).setServerLocation("loc2").build();

        QueryUserOnlineResp mockResponse =
                QueryUserOnlineResp.newBuilder()
                        .addAllUserOnlineInfos(Arrays.asList(info100L, info300L))
                        .build();

        when(mockStorageService.getUserOnline(any(QueryUserOnlineReq.class)))
                .thenReturn(mockResponse);

        clientPool.execute(
                stub -> {
                    QueryUserOnlineResp response = stub.queryUserOnline(request);

                    assertNotNull(response);
                    assertEquals(2, response.getUserOnlineInfosCount());
                    assertTrue(response.getUserOnlineInfosList().contains(info100L));
                    assertTrue(response.getUserOnlineInfosList().contains(info300L));
                    assertFalse(response.getUserOnlineInfosList().contains(info200L));
                });

        ArgumentCaptor<QueryUserOnlineReq> captor =
                ArgumentCaptor.forClass(QueryUserOnlineReq.class);
        verify(mockStorageService, times(1)).getUserOnline(captor.capture());

        QueryUserOnlineReq capturedRequest = captor.getValue();
        assertEquals(3, capturedRequest.getUserIdsCount());
        assertTrue(capturedRequest.getUserIdsList().containsAll(Arrays.asList(100L, 200L, 300L)));
    }

    @Test
    void testMultipleSetUserOnlineCalls() throws Exception {
        UserOnlineReq request1 =
                UserOnlineReq.newBuilder()
                        .setUserId(1L)
                        .setDeviceId("device-1")
                        .setIsOnline(true)
                        .build();

        UserOnlineReq request2 =
                UserOnlineReq.newBuilder()
                        .setUserId(2L)
                        .setDeviceId("device-2")
                        .setIsOnline(false)
                        .build();

        clientPool.execute(stub -> stub.setUserOnline(request1));
        clientPool.execute(stub -> stub.setUserOnline(request2));

        verify(mockStorageService, times(2)).setUserOnline(any(UserOnlineReq.class));
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
