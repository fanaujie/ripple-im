package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.shaded.netty.channel.Channel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultOnlineUser implements OnlineUser {

    private record Key(long userId, String deviceId) {}

    private final Map<Key, Channel> userChannelMap = new ConcurrentHashMap<>();
    private final Map<Channel, Key> channelUserMap = new ConcurrentHashMap<>();

    @Override
    public void add(long userId, String deviceId, Channel channel) {
        Key key = new Key(userId, deviceId);
        userChannelMap.put(key, channel);
        channelUserMap.put(channel, key);
    }

    @Override
    public Optional<Channel> get(long userId, String deviceId) {
        Key key = new Key(userId, deviceId);
        Channel c = userChannelMap.get(key);
        return Optional.ofNullable(c);
    }

    @Override
    public void remove(Channel channel) {
        Key key = channelUserMap.remove(channel);
        if (key != null) {
            userChannelMap.remove(key);
        }
    }
}
