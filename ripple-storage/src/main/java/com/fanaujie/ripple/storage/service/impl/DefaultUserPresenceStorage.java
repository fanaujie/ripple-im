package com.fanaujie.ripple.storage.service.impl;

import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;
import com.fanaujie.ripple.storage.service.UserPresenceStorage;
import com.fanaujie.ripple.storage.service.utils.LuaUtils;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.ArrayList;
import java.util.List;

public class DefaultUserPresenceStorage implements UserPresenceStorage {
    private final RedissonClient redissonClient;
    private final String userOnlineLuaScript;

    public DefaultUserPresenceStorage(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.userOnlineLuaScript = LuaUtils.loadScript("lua/get_user_online.lua");
    }

    @Override
    public void setUserOnline(UserOnlineReq request) {
        if (request.getIsOnline()) {
            redissonClient
                    .getBucket(
                            getUserPresenceKey(request.getUserId(), request.getDeviceId()),
                            StringCodec.INSTANCE)
                    .set(request.getServerLocation());
        } else {
            redissonClient
                    .getBucket(
                            getUserPresenceKey(request.getUserId(), request.getDeviceId()),
                            StringCodec.INSTANCE)
                    .delete();
        }
    }

    @Override
    public QueryUserOnlineResp getUserOnline(QueryUserOnlineReq request) {
        QueryUserOnlineResp.Builder builder = QueryUserOnlineResp.newBuilder();
        if (request.getUserIdsList().isEmpty()) {
            return builder.build();
        }

        // Convert user IDs to string array for Lua script
        List<Object> keys = new ArrayList<>();
        for (Long userId : request.getUserIdsList()) {
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
                    long userId = Long.parseLong(result.get(i));
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

    private String getUserPresenceKey(long userId, String deviceId) {
        return String.format("user_presence:%s:%s", userId, deviceId);
    }

    private String getUserPresenceKeyPattern(long userId) {
        return String.format("user_presence:%s:*", userId);
    }
}
