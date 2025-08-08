package com.fanaujie.ripple.msggateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "message-gateway", mixinStandardHelpOptions = true, version = "1.0",
        description = "Message Gateway server with WebSocket and gRPC support")
public class Application implements Runnable {

    Logger logger = LoggerFactory.getLogger(Application.class);

    private int websocketPort;
    private int grpcPort;
    private String serverName;

    @Spec
    CommandSpec spec;

    @Option(names = {"-w", "--websocket-port"}, description = "WebSocket server port (default: ${DEFAULT-VALUE})",
            defaultValue = "8080")
    public void setWebsocketPort(int value) {
        if (value < 1 || value > 65535) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Invalid WebSocket port number: " + value + ". Port must be between 1 and 65535.");
        }
        this.websocketPort = value;
    }

    @Option(names = {"-g", "--grpc-port"}, description = "gRPC server port (default: ${DEFAULT-VALUE})",
            defaultValue = "9090")
    public void setGrpcPort(int value) {
        if (value < 1 || value > 65535) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Invalid gRPC port number: " + value + ". Port must be between 1 and 65535.");
        }
        this.grpcPort = value;
    }

    @Option(names = {"-n", "--server-name"}, description = "Server name identifier (default: ${DEFAULT-VALUE})",
            defaultValue = "message-gateway-01")
    public void setServerName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Server name cannot be null or empty.");
        }
        if (!value.matches("^[a-zA-Z0-9][a-zA-Z0-9-_]*[a-zA-Z0-9]$|^[a-zA-Z0-9]$")) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Invalid server name: '" + value + "'. Server name must contain only alphanumeric characters, hyphens, and underscores, and cannot start or end with special characters.");
        }
        this.serverName = value.trim();
    }

    @Override
    public void run() {
        logger.info("Starting Message Gateway server...");
        logger.info("Server name: {}", serverName);
        logger.info("WebSocket port: {}", websocketPort);
        logger.info("gRPC port: {}", grpcPort);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Application()).execute(args);
        System.exit(exitCode);
    }
}