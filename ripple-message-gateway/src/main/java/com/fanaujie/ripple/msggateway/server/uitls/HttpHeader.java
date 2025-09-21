package com.fanaujie.ripple.msggateway.server.uitls;

import com.fanaujie.ripple.shaded.netty.handler.codec.http.HttpHeaders;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

public class HttpHeader {
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_RIPPLE_DEVICE_ID = "Ripple-Device-ID";

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class RippleHeader {
        private String token;
        private String deviceId;
    }

    public static Optional<RippleHeader> extractTokenAndDeviceId(HttpHeaders headers) {
        String authHeader = headers.get(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String deviceId = headers.get(HEADER_RIPPLE_DEVICE_ID);
            if (deviceId != null && !deviceId.isEmpty()) {
                return Optional.of(new RippleHeader(token, deviceId));
            }
        }
        return Optional.empty();
    }
}
