package com.fanaujie.ripple.msggateway.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WsClientConfig {
    
    @Builder.Default
    private String host = "localhost";
    
    @Builder.Default
    private int port = 8080;
    
    @Builder.Default
    private String path = "/ws";
    
    @Builder.Default
    private long timeoutMillis = 5000;
    
    @Builder.Default
    private int maxRetries = 3;
    
    @Builder.Default
    private long retryDelayMillis = 1000;
    
    public static WsClientConfig defaultConfig() {
        return WsClientConfig.builder().build();
    }
    
    public static WsClientConfig forServer(String host, int port, String path) {
        return WsClientConfig.builder()
                .host(host)
                .port(port)
                .path(path)
                .build();
    }
}