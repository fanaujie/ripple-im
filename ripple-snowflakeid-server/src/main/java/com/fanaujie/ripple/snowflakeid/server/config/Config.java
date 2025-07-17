package com.fanaujie.ripple.snowflakeid.server.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Config {
    private int port;
    private String zookeeperAddr;
    private int workerId;
}
