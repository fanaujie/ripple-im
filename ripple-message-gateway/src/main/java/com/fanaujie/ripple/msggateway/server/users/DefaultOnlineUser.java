package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.shaded.netty.channel.Channel;

public class DefaultOnlineUser implements OnlineUser {
    @Override
    public void add(long userId, String deviceId, Channel channel) {}

    @Override
    public void remove(Channel channel) {}
}
