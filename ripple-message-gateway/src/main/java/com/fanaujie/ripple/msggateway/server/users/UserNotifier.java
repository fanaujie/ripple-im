package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.shaded.netty.channel.Channel;

public interface UserNotifier {
    void push(Channel channel, PushMessageRequest request);
}
