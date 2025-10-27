package com.fanaujie.ripple.communication.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.function.Function;

public class GrpcClient<T> {

    private final ManagedChannel channel;
    private final T stub;

    public GrpcClient(String serverAddr, Function<ManagedChannel, T> stubFactory) {
        this.channel =
                ManagedChannelBuilder.forTarget(String.format("dns:///%s", serverAddr))
                        .usePlaintext()
                        .defaultLoadBalancingPolicy("round_robin")
                        .build();
        this.stub = stubFactory.apply(channel);
    }

    public T getStub() {
        return stub;
    }

    public ManagedChannel getChannel() {
        return channel;
    }
}
