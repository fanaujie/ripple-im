package com.fanaujie.ripple.communication.batch;

import java.util.List;

public interface BatchProcessorFactory<T> {
    interface BatchProcessor<T> {
        void process(List<T> batch);
    }

    BatchProcessor<T> create();
}
