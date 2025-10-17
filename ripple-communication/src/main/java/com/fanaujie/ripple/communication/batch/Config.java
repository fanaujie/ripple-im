package com.fanaujie.ripple.communication.batch;

public record Config(int queueSize, int workerSize, int batchMaxSize, long queueTimeoutMs) {}
