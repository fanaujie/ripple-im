package com.fanaujie.ripple.msggateway.server.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsConfig {
    private int port;
    private String wsPath;
}
