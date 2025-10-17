package com.fanaujie.ripple.communication.grpc.client;

import io.grpc.ManagedChannel;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.function.Consumer;
import java.util.function.Function;

public class GrpcClientPool<T> {
    private final GenericObjectPool<GrpcClient<T>> pool;

    public GrpcClientPool(String serverAddr, Function<ManagedChannel, T> stubFactory) {
        GenericObjectPoolConfig<GrpcClient<T>> config = new GenericObjectPoolConfig<>();
        this.pool =
                new GenericObjectPool<>(new GrpcClientFactory<>(serverAddr, stubFactory), config);
    }

    public void execute(Consumer<T> action) throws Exception {
        GrpcClient<T> client = null;
        try {
            client = pool.borrowObject();
            action.accept(client.getStub());
        } finally {
            if (client != null) {
                pool.returnObject(client);
            }
        }
    }

    public void close() {
        pool.close();
    }
}
