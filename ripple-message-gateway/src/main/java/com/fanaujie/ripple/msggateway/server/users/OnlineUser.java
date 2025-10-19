package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.shaded.netty.channel.Channel;

import java.util.Optional;

public interface OnlineUser {
    void add(long userId, String deviceId, Channel channel);

    Optional<Channel> get(long userId, String deviceId);

    void remove(Channel session);
}
