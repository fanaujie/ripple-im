package com.fanaujie.ripple.storage.service.impl;
//
// import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
// import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
// import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;
// import com.redis.testcontainers.RedisContainer;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.redisson.Redisson;
// import org.redisson.api.RedissonClient;
// import org.redisson.client.codec.StringCodec;
// import org.redisson.config.Config;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
// import org.testcontainers.utility.DockerImageName;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// @Testcontainers
// class DefaultUserPresenceStorageServiceTest {
//
//    @Container
//    private static final RedisContainer redisContainer =
//            new RedisContainer(DockerImageName.parse("redis:latest"));
//
//    private RedissonClient redissonClient;
//    private DefaultUserPresenceStorageService userPresenceService;
//
//    @BeforeEach
//    void setUp() {
//        Config config = new Config();
//        config.useSingleServer()
//                .setAddress(
//                        "redis://"
//                                + redisContainer.getRedisHost()
//                                + ":"
//                                + redisContainer.getRedisPort());
//        redissonClient = Redisson.create(config);
//        userPresenceService = new DefaultUserPresenceStorageService(redissonClient);
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (redissonClient != null) {
//            redissonClient.getKeys().flushall();
//            redissonClient.shutdown();
//        }
//    }
//
//    @Test
//    void testSetUserOnline_UserGoesOnline() {
//        long userId = 1000L;
//        String deviceId = "device-001";
//
//        UserOnlineReq request =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(deviceId)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//
//        userPresenceService.setUserOnline(request);
//
//        // Verify the key exists in Redis
//        String expectedKey = String.format("user_presence:%s:%s", userId, deviceId);
//        assertTrue(redissonClient.getBucket(expectedKey, StringCodec.INSTANCE).isExists());
//        assertEquals(
//                "test-server-1", redissonClient.getBucket(expectedKey,
// StringCodec.INSTANCE).get());
//    }
//
//    @Test
//    void testSetUserOnline_UserGoesOffline() {
//        long userId = 1001L;
//        String deviceId = "device-002";
//
//        // First, set user online
//        UserOnlineReq onlineRequest =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(deviceId)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//        userPresenceService.setUserOnline(onlineRequest);
//
//        // Then, set user offline
//        UserOnlineReq offlineRequest =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(deviceId)
//                        .setIsOnline(false)
//                        .build();
//        userPresenceService.setUserOnline(offlineRequest);
//
//        // Verify the key is deleted
//        String expectedKey = String.format("user_presence:%s:%s", userId, deviceId);
//        assertFalse(redissonClient.getBucket(expectedKey, StringCodec.INSTANCE).isExists());
//    }
//
//    @Test
//    void testSetUserOnline_MultipleDevices() {
//        long userId = 1002L;
//        String device1 = "device-003";
//        String device2 = "device-004";
//
//        // Set user online on device 1
//        UserOnlineReq request1 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device1)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//        userPresenceService.setUserOnline(request1);
//
//        // Set user online on device 2
//        UserOnlineReq request2 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device2)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-2")
//                        .build();
//        userPresenceService.setUserOnline(request2);
//
//        // Verify both keys exist
//        String key1 = String.format("user_presence:%s:%s", userId, device1);
//        String key2 = String.format("user_presence:%s:%s", userId, device2);
//        assertTrue(redissonClient.getBucket(key1, StringCodec.INSTANCE).isExists());
//        assertTrue(redissonClient.getBucket(key2, StringCodec.INSTANCE).isExists());
//    }
//
//    @Test
//    void testGetUserOnline_SingleUserOnline() {
//        long userId = 2000L;
//        String deviceId = "device-005";
//
//        // Set user online
//        UserOnlineReq setRequest =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(deviceId)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//        userPresenceService.setUserOnline(setRequest);
//
//        // Query user online status
//        QueryUserOnlineReq queryRequest =
//                QueryUserOnlineReq.newBuilder().addUserIds(userId).build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        assertEquals(1, response.getUserOnlineInfosCount());
//        assertEquals(userId, response.getUserOnlineInfos(0).getUserId());
//        assertEquals(deviceId, response.getUserOnlineInfos(0).getDeviceId());
//        assertEquals("test-server-1", response.getUserOnlineInfos(0).getServerLocation());
//    }
//
//    @Test
//    void testGetUserOnline_SingleUserOffline() {
//        long userId = 2001L;
//
//        // Query user online status (user was never set online)
//        QueryUserOnlineReq queryRequest =
//                QueryUserOnlineReq.newBuilder().addUserIds(userId).build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        assertEquals(0, response.getUserOnlineInfosCount());
//    }
//
//    @Test
//    void testGetUserOnline_MultipleUsers_SomeOnline() {
//        long user1 = 2002L;
//        long user2 = 2003L;
//        long user3 = 2004L;
//
//        // Set user1 and user3 online
//        UserOnlineReq request1 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(user1)
//                        .setDeviceId("device-006")
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//        userPresenceService.setUserOnline(request1);
//
//        UserOnlineReq request3 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(user3)
//                        .setDeviceId("device-007")
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-2")
//                        .build();
//        userPresenceService.setUserOnline(request3);
//
//        // Query all three users
//        QueryUserOnlineReq queryRequest =
//                QueryUserOnlineReq.newBuilder()
//                        .addUserIds(user1)
//                        .addUserIds(user2)
//                        .addUserIds(user3)
//                        .build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        assertEquals(2, response.getUserOnlineInfosCount());
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getUserId() == user1));
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getUserId() == user3));
//        assertFalse(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getUserId() == user2));
//    }
//
//    @Test
//    void testGetUserOnline_UserWithMultipleDevices() {
//        long userId = 2005L;
//        String device1 = "device-008";
//        String device2 = "device-009";
//
//        // Set user online on multiple devices
//        UserOnlineReq request1 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device1)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//        userPresenceService.setUserOnline(request1);
//
//        UserOnlineReq request2 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device2)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-2")
//                        .build();
//        userPresenceService.setUserOnline(request2);
//
//        // Query user online status
//        QueryUserOnlineReq queryRequest =
//                QueryUserOnlineReq.newBuilder().addUserIds(userId).build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        // User should be reported as online with 2 devices
//        assertEquals(2, response.getUserOnlineInfosCount());
//        // All UserOnlineInfo should have the same userId
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .allMatch(info -> info.getUserId() == userId));
//        // Should have both device IDs
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getDeviceId().equals(device1)));
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getDeviceId().equals(device2)));
//        // Should have both server locations
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getServerLocation().equals("test-server-1")));
//        assertTrue(
//                response.getUserOnlineInfosList().stream()
//                        .anyMatch(info -> info.getServerLocation().equals("test-server-2")));
//    }
//
//    @Test
//    void testGetUserOnline_UserWithOneDeviceOffline() {
//        long userId = 2006L;
//        String device1 = "device-010";
//        String device2 = "device-011";
//
//        // Set user online on multiple devices
//        UserOnlineReq request1 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device1)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-1")
//                        .build();
//        userPresenceService.setUserOnline(request1);
//
//        UserOnlineReq request2 =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device2)
//                        .setIsOnline(true)
//                        .setServerLocation("test-server-2")
//                        .build();
//        userPresenceService.setUserOnline(request2);
//
//        // Set device1 offline
//        UserOnlineReq offlineRequest =
//                UserOnlineReq.newBuilder()
//                        .setUserId(userId)
//                        .setDeviceId(device1)
//                        .setIsOnline(false)
//                        .build();
//        userPresenceService.setUserOnline(offlineRequest);
//
//        // Query user online status
//        QueryUserOnlineReq queryRequest =
//                QueryUserOnlineReq.newBuilder().addUserIds(userId).build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        // User should still be online with only device2
//        assertEquals(1, response.getUserOnlineInfosCount());
//        assertEquals(userId, response.getUserOnlineInfos(0).getUserId());
//        assertEquals(device2, response.getUserOnlineInfos(0).getDeviceId());
//        assertEquals("test-server-2", response.getUserOnlineInfos(0).getServerLocation());
//    }
//
//    @Test
//    void testGetUserOnline_EmptyRequest() {
//        QueryUserOnlineReq queryRequest = QueryUserOnlineReq.newBuilder().build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        assertEquals(0, response.getUserOnlineInfosCount());
//    }
//
//    @Test
//    void testGetUserOnline_AllUsersOffline() {
//        long user1 = 2007L;
//        long user2 = 2008L;
//
//        // Query users that were never online
//        QueryUserOnlineReq queryRequest =
//                QueryUserOnlineReq.newBuilder().addUserIds(user1).addUserIds(user2).build();
//
//        QueryUserOnlineResp response = userPresenceService.getUserOnline(queryRequest);
//
//        assertEquals(0, response.getUserOnlineInfosCount());
//    }
// }
