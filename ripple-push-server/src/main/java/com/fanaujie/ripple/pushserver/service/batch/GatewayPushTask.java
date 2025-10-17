package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;

import java.util.List;

public record GatewayPushTask(
        String serverAddress, List<UserOnlineInfo> userInfos, MessagePayload messagePayload) {}
