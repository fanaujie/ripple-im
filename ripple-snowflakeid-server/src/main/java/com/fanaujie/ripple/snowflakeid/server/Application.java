package com.fanaujie.ripple.snowflakeid.server;

import com.fanaujie.ripple.snowflakeid.server.config.Config;
import com.fanaujie.ripple.snowflakeid.server.service.snowflakeid.SnowflakeIdService;
import com.fanaujie.ripple.snowflakeid.server.service.zookeeper.ZookeeperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "snowflakeid-server",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Snowflake ID generation server")
public class Application implements Runnable {

    Logger logger = LoggerFactory.getLogger(Application.class);

    private int port;
    private String zookeeperAddr;

    @Spec CommandSpec spec;

    @Option(
            names = {"-p", "--port"},
            description = "Server port (default: ${DEFAULT-VALUE})",
            defaultValue = "8081")
    public void setPort(int value) {
        if (value < 1 || value > 65535) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Invalid port number: " + value + ". Port must be between 1 and 65535.");
        }
        this.port = value;
    }

    @Option(
            names = {"-z", "--zookeeper-addr"},
            description = "Zookeeper address (default: ${DEFAULT-VALUE})",
            defaultValue = "localhost:2181")
    public void setZookeeperAddr(String value) {
        boolean invalid = false;
        // parse the address to check if it's valid
        if (value == null || value.isEmpty()) {
            invalid = true;
        } else {
            String[] hostPorts = value.split(",");
            for (String hostPort : hostPorts) {
                String[] parts = hostPort.split(":");
                if (parts.length != 2 || parts[0].isEmpty() || !parts[1].matches("\\d+")) {
                    invalid = true;
                    break;
                } else {
                    int portNumber = Integer.parseInt(parts[1]);
                    if (portNumber < 1 || portNumber > 65535) {
                        invalid = true;
                        break;
                    }
                }
            }
        }

        if (invalid) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Invalid Zookeeper address: '" + value + "'. Expected format: host:port");
        }
        zookeeperAddr = value;
    }

    @Override
    public void run() {
        int workerId = acquiredWorkerId();
        if (workerId == 0) {
            logger.error("Failed to acquire a worker ID from Zookeeper.");
        } else {
            Config cfg = new Config(port, zookeeperAddr, workerId);
            logger.info("Acquired worker ID: {}", workerId);
            SnowflakeIdService snowflakeIdService = new SnowflakeIdService(cfg);
            try {
                snowflakeIdService.start();
            } catch (Exception e) {
                logger.error("Error starting Snowflake ID service", e);
                throw new RuntimeException(e);
            } finally {
                snowflakeIdService.stop();
                logger.info("Snowflake ID service stopped.");
            }
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Application()).execute(args);
        System.exit(exitCode);
    }

    private int acquiredWorkerId() {
        int workerId = 0;
        ZookeeperService zk = null;
        try {
            zk = new ZookeeperService(zookeeperAddr, "/snowflakeid/lock", "/snowflakeid/workerIds");
            workerId = zk.acquiredWorkerId();
        } catch (Exception e) {
            logger.error("Error acquiring worker ID from Zookeeper", e);
        } finally {
            if (zk != null) {
                zk.close();
            }
        }
        return workerId;
    }
}
