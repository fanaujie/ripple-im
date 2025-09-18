package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.shaded.netty.channel.Channel;

public interface OnlineUser {
    void add(long userId, String deviceId, Channel channel);

    void remove(Channel channel);
}
