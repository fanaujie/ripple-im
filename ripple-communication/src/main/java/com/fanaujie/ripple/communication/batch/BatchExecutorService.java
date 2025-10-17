package com.fanaujie.ripple.communication.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class BatchExecutorService<T> {
    private final ExecutorService executorService;
    private final BlockingQueue<T> queue;
    private final List<BatchWorker<T>> workers;

    public BatchExecutorService(Config config, BatchProcessorFactory<T> processorFactory) {
        this.queue = new LinkedBlockingQueue<>(config.queueSize());
        this.executorService = Executors.newFixedThreadPool(config.workerSize());
        this.workers = new ArrayList<>();
        for (int i = 0; i < config.workerSize(); i++) {
            BatchWorker<T> w =
                    new BatchWorker<>(
                            this.queue,
                            config.batchMaxSize(),
                            config.queueTimeoutMs(),
                            processorFactory.create());
            this.workers.add(w);
            this.executorService.submit(w);
        }
    }

    public void push(T item) throws InterruptedException {
        this.queue.put(item);
    }

    public void shutdown() {
        this.executorService.shutdownNow();
    }

    public void awaitTermination() throws InterruptedException {
        this.executorService.awaitTermination(1, TimeUnit.SECONDS);
    }
}
