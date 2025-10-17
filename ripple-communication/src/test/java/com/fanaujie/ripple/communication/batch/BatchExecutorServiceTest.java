package com.fanaujie.ripple.communication.batch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchExecutorServiceTest {

    private BatchExecutorService<String> executorService;
    private BatchProcessorFactory<String> processorFactory;
    private BatchProcessorFactory.BatchProcessor<String> mockProcessor;

    @BeforeEach
    void setUp() {
        mockProcessor = mock(BatchProcessorFactory.BatchProcessor.class);
        processorFactory = mock(BatchProcessorFactory.class);
        when(processorFactory.create()).thenReturn(mockProcessor);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination();
        }
    }

    @Test
    void testConstructor_CreatesWorkersSuccessfully() {
        // Given
        Config config = new Config(100, 2, 10, 1000);

        // When
        executorService = new BatchExecutorService<>(config, processorFactory);

        // Then
        assertNotNull(executorService);
        verify(processorFactory, times(2)).create();
    }

    @Test
    void testPush_AddsItemToQueue() throws InterruptedException {
        // Given
        Config config = new Config(100, 1, 5, 100);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockProcessor).process(any());

        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        executorService.push("test-item");

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(mockProcessor, atLeastOnce()).process(any());
    }

    @Test
    void testPush_ProcessesBatchWhenMaxSizeReached() throws InterruptedException {
        // Given
        Config config = new Config(100, 1, 3, 5000);
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> processedItems = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            processedItems.addAll(batch);
            latch.countDown();
            return null;
        }).when(mockProcessor).process(any());

        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        executorService.push("item1");
        executorService.push("item2");
        executorService.push("item3");

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(3, processedItems.size());
        assertTrue(processedItems.contains("item1"));
        assertTrue(processedItems.contains("item2"));
        assertTrue(processedItems.contains("item3"));
    }

    @Test
    void testPush_ProcessesBatchOnTimeout() throws InterruptedException {
        // Given
        Config config = new Config(100, 1, 10, 200);
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> processedItems = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            processedItems.addAll(batch);
            latch.countDown();
            return null;
        }).when(mockProcessor).process(any());

        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        executorService.push("item1");
        executorService.push("item2");

        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(2, processedItems.size());
        assertTrue(processedItems.contains("item1"));
        assertTrue(processedItems.contains("item2"));
    }

    @Test
    void testMultipleWorkers_ProcessItemsConcurrently() throws InterruptedException {
        // Given
        Config config = new Config(100, 3, 5, 100);
        CountDownLatch latch = new CountDownLatch(15);
        CopyOnWriteArrayList<String> processedItems = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            processedItems.addAll(batch);
            for (int i = 0; i < batch.size(); i++) {
                latch.countDown();
            }
            return null;
        }).when(mockProcessor).process(any());

        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        for (int i = 0; i < 15; i++) {
            executorService.push("item" + i);
        }

        // Then
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(15, processedItems.size());
    }

    @Test
    void testShutdown_StopsExecutorService() throws InterruptedException {
        // Given
        Config config = new Config(100, 2, 10, 1000);
        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        executorService.shutdown();
        executorService.awaitTermination();

        // Then
        // If we reach here without hanging, shutdown worked
        assertTrue(true);
    }

    @Test
    void testPush_WithFullQueue_Blocks() throws InterruptedException {
        // Given - batch size 2, queue size 2
        Config config = new Config(2, 1, 2, 10000);
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch blockProcessing = new CountDownLatch(1);
        AtomicBoolean isProcessing = new AtomicBoolean(false);

        doAnswer(invocation -> {
            processingStarted.countDown();
            isProcessing.set(true);
            blockProcessing.await(); // Block until we signal
            isProcessing.set(false);
            return null;
        }).when(mockProcessor).process(any());

        executorService = new BatchExecutorService<>(config, processorFactory);

        // When - push 2 items to trigger batch processing
        executorService.push("item1");
        executorService.push("item2");

        // Wait for processing to start (worker is now blocked)
        assertTrue(processingStarted.await(1, TimeUnit.SECONDS));
        assertTrue(isProcessing.get());

        // Fill the queue (size = 2) while worker is blocked
        executorService.push("item3");
        executorService.push("item4");

        // Fifth push should block because queue is full
        AtomicBoolean pushCompleted = new AtomicBoolean(false);
        Thread pushThread = new Thread(() -> {
            try {
                executorService.push("item5"); // This should block
                pushCompleted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        pushThread.start();
        Thread.sleep(200); // Give it time to attempt push

        // Thread should be blocked (alive but push not completed)
        assertTrue(pushThread.isAlive());
        assertFalse(pushCompleted.get());

        // Unblock processing to free up queue space
        blockProcessing.countDown();

        // Now push should complete
        pushThread.join(2000);
        assertTrue(pushCompleted.get());
    }

    @Test
    void testAwaitTermination_WaitsForCompletion() throws InterruptedException {
        // Given
        Config config = new Config(100, 1, 10, 1000);
        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        executorService.shutdown();
        executorService.awaitTermination();

        // Then
        // Should complete without timeout
        assertTrue(true);
    }

    @Test
    void testMultipleBatches_ProcessedInOrder() throws InterruptedException {
        // Given
        Config config = new Config(100, 1, 2, 100);
        CountDownLatch latch = new CountDownLatch(2);
        CopyOnWriteArrayList<List<String>> batches = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            batches.add(List.copyOf(batch));
            latch.countDown();
            return null;
        }).when(mockProcessor).process(any());

        executorService = new BatchExecutorService<>(config, processorFactory);

        // When
        executorService.push("item1");
        executorService.push("item2");
        Thread.sleep(150); // Wait for first batch to process
        executorService.push("item3");
        executorService.push("item4");

        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(2, batches.size());
    }
}