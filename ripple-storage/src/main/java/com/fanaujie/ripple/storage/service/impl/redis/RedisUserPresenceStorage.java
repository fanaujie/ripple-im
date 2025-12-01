package com.fanaujie.ripple.storage.service.impl.redis;

import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;
import com.fanaujie.ripple.storage.service.UserPresenceStorage;
import com.fanaujie.ripple.storage.service.utils.LuaUtils;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RedisUserPresenceStorage implements UserPresenceStorage {
    private final RedissonClient redissonClient;
    private final String userOnlineLuaScript;
    private final int presenceTimeoutSeconds;

    public RedisUserPresenceStorage(RedissonClient redissonClient, int presenceTimeoutSeconds) {
        this.redissonClient = redissonClient;
        this.userOnlineLuaScript = LuaUtils.loadScript("lua/get_user_online.lua");
        this.presenceTimeoutSeconds = presenceTimeoutSeconds;
    }

    @Override
    public void setUserOnline(UserOnlineReq request) {
        if (request.getIsOnline()) {
            redissonClient
                    .getBucket(
                            getUserPresenceKey(request.getUserId(), request.getDeviceId()),
                            StringCodec.INSTANCE)
                    .set(
                            request.getServerLocation(),
                            Duration.ofSeconds(this.presenceTimeoutSeconds));
        } else {
            redissonClient
                    .getBucket(
                            getUserPresenceKey(request.getUserId(), request.getDeviceId()),
                            StringCodec.INSTANCE)
                    .delete();
        }
    }

    @Override
    public void setUserOnlineBatch(List<UserOnlineReq> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        // Use batch operations for better performance
        var batch = redissonClient.createBatch();

        for (UserOnlineReq request : requests) {
            String key = getUserPresenceKey(request.getUserId(), request.getDeviceId());
            if (request.getIsOnline()) {
                batch.getBucket(key, StringCodec.INSTANCE)
                        .setAsync(
                                request.getServerLocation(),
                                Duration.ofSeconds(this.presenceTimeoutSeconds));
            } else {
                batch.getBucket(key, StringCodec.INSTANCE).deleteAsync();
            }
        }

        // Execute all operations in a single batch
        batch.execute();
    }

    @Override
    public QueryUserOnlineResp getUserOnline(QueryUserOnlineReq request) {
        QueryUserOnlineResp.Builder builder = QueryUserOnlineResp.newBuilder();
        if (request.getUserIdsList().isEmpty()) {
            return builder.build();
        }

        // Convert user IDs to string array for Lua script
        List<Object> keys = new ArrayList<>();
        for (String userId : request.getUserIdsList()) {
            keys.add(getUserPresenceKeyPattern(userId));
        }

        // Execute Lua script
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        List<String> result =
                script.eval(
                        RScript.Mode.READ_ONLY,
                        userOnlineLuaScript,
                        RScript.ReturnType.MULTI,
                        keys);

        // Parse result: [userId1, deviceId1, serverLocation1, userId2, deviceId2, serverLocation2,
        // ...]
        if (result != null) {
            for (int i = 0; i < result.size(); i += 3) {
                if (i + 2 < result.size()) {
                    String userId = result.get(i);
                    String deviceId = result.get(i + 1);
                    String serverLocation = result.get(i + 2);

                    UserOnlineInfo info =
                            UserOnlineInfo.newBuilder()
                                    .setUserId(userId)
                                    .setDeviceId(deviceId)
                                    .setServerLocation(serverLocation)
                                    .build();
                    builder.addUserOnlineInfos(info);
                }
            }
        }

        return builder.build();
    }

    private String getUserPresenceKey(String userId, String deviceId) {
        return String.format("user_presence:%s:%s", userId, deviceId);
    }

    private String getUserPresenceKeyPattern(String userId) {
        return String.format("user_presence:%s:*", userId);
    }
}
