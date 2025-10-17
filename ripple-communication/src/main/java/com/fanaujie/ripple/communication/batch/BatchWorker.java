package com.fanaujie.ripple.communication.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatchWorker<T> implements Runnable {

    private final BlockingQueue<T> queue;
    private final int batchMaxSize;
    private final long batchTimeoutMs;
    private final BatchProcessorFactory.BatchProcessor<T> processor;
    private final AtomicBoolean runningFlag = new AtomicBoolean(true);

    public BatchWorker(
            BlockingQueue<T> queue,
            int batchMaxSize,
            long batchTimeoutMs,
            BatchProcessorFactory.BatchProcessor<T> processor) {
        this.queue = queue;
        this.batchMaxSize = batchMaxSize;
        this.batchTimeoutMs = batchTimeoutMs;
        this.processor = processor;
    }

    public void stop() {
        this.runningFlag.set(false);
    }

    @Override
    public void run() {
        List<T> batch = new ArrayList<>();
        boolean isRunning = true;
        while (runningFlag.get() && isRunning) {
            while (true) {
                T item = null;
                try {
                    item = queue.poll(this.batchTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    isRunning = false;
                    break;
                }
                if (item == null) {
                    break;
                } else {
                    batch.add(item);
                    if (batch.size() == this.batchMaxSize) {
                        break;
                    }
                }
            }
            if (isRunning && !batch.isEmpty()) {
                processBatch(batch);
                batch.clear();
            }
        }
    }

    private void processBatch(List<T> batch) {
        this.processor.process(batch);
    }
}
