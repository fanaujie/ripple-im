package com.fanaujie.ripple.communication.grpc.client;

import io.grpc.ManagedChannel;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class GrpcClientFactory<T> extends BasePooledObjectFactory<GrpcClient<T>> {
    private final Logger logger = LoggerFactory.getLogger(GrpcClientFactory.class);

    private final String serverAddr;
    private final Function<ManagedChannel, T> stubFactory;

    public GrpcClientFactory(String serverAddr, Function<ManagedChannel, T> stubFactory) {
        this.serverAddr = serverAddr;
        this.stubFactory = stubFactory;
    }

    @Override
    public GrpcClient<T> create() throws Exception {
        return new GrpcClient<>(this.serverAddr, this.stubFactory);
    }

    @Override
    public PooledObject<GrpcClient<T>> wrap(GrpcClient<T> grpcClient) {
        return new DefaultPooledObject<>(grpcClient);
    }

    @Override
    public void destroyObject(PooledObject<GrpcClient<T>> p) throws Exception {
        p.getObject().getChannel().shutdownNow();
    }
}
