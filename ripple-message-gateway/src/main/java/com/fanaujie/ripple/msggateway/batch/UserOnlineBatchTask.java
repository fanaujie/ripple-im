package com.fanaujie.ripple.msggateway.batch;

public record UserOnlineBatchTask(
        String userId, String deviceId, boolean isOnline, String serverLocation) {}
