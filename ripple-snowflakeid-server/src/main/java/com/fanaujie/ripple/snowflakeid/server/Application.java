package com.fanaujie.ripple.snowflakeid.server;

import com.fanaujie.ripple.snowflakeid.server.zookeeper.ZookeeperService;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "snowflakeid-server", mixinStandardHelpOptions = true, version = "1.0",
         description = "Snowflake ID generation server")
public class Application implements Runnable {

    private int port;
    private String zookeeperAddr;

    @Spec CommandSpec spec;

    @Option(names = {"-s", "--host"}, description = "Server host (default: ${DEFAULT-VALUE})",
            defaultValue = "localhost")
    private String host;

    @Option(names = {"-p", "--port"}, description = "Server port (default: ${DEFAULT-VALUE})",
            defaultValue = "8080")
    public void setPort(int value) {
        if (value < 1 || value > 65535) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Invalid port number: " + value + ". Port must be between 1 and 65535.");
        }
        this.port = value;
    }


    @Option(names = {"-z", "--zookeeper-addr"}, description = "Zookeeper address (default: ${DEFAULT-VALUE})",
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
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Invalid Zookeeper address: '" + value + "'. Expected format: host:port");
        }
        zookeeperAddr = value;
    }



    @Override
    public void run() {
        System.out.println("Starting Snowflake ID Server...");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        System.out.println("Zookeeper Address: " + zookeeperAddr);

        try {
            ZookeeperService zk = new ZookeeperService(zookeeperAddr, "/snowflakeid/lock", "/snowflakeid/workerIds");
            System.out.println("WorkerID: "+ zk.acquiredWorkerId());
            Thread.sleep(1000); // Simulate some work
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            System.err.println("Error initializing Zookeeper service: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Application()).execute(args);
        System.exit(exitCode);
    }
}

