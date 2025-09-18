package com.fanaujie.ripple.msggateway.server.ws.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsConfig {
    private int port;
    private String wsPath;
    private int idleSeconds;
}
