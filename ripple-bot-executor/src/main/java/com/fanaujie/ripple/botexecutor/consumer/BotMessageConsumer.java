package com.fanaujie.ripple.botexecutor.consumer;

import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfig;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import org.apache.kafka.clients.consumer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

public class BotMessageConsumer implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(BotMessageConsumer.class);
    private final KafkaConsumer<String, MessagePayload> consumer;
    private final ExecutorService workerPool;
    private final BotPayloadHandler payloadHandler;
    private volatile boolean running = true;
    private boolean paused = false;

    private final int queueHighWatermark;
    private final int queueLowWatermark;

    public BotMessageConsumer(KafkaConsumerConfig config, ExecutorService workerPool, BotPayloadHandler payloadHandler, int poolQueueCapacity) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getGroupId());
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, config.getClientId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, config.getKeyDeserializer());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, config.getValueDeserializer());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.getMaxPollRecords());
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, config.getFetchMinBytes());
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, config.getFetchMaxWaitMs());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(config.getTopic()));
        this.workerPool = workerPool;
        this.payloadHandler = payloadHandler;
        this.queueHighWatermark = (int) (poolQueueCapacity * 0.8);
        this.queueLowWatermark = (int) (poolQueueCapacity * 0.2);
    }
    
    @Override
    public void run() {
        while (running) {
            try {
                checkQueueAndPause();
                ConsumerRecords<String, MessagePayload> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, MessagePayload> record : records) {
                    payloadHandler.process(record.key(), record.value());
                }
                consumer.commitAsync();
            } catch (Exception e) {
                logger.error("Error in bot consumer loop", e);
            }
        }
        consumer.close();
    }
    
    private void checkQueueAndPause() {
        int queueSize = ((ThreadPoolExecutor) workerPool).getQueue().size();
        if (queueSize >= queueHighWatermark && !paused) {
            logger.warn("Worker queue is full (size {}). Pausing consumption.", queueSize);
            consumer.pause(consumer.assignment());
            paused = true;
        } else if (queueSize <= queueLowWatermark && paused) {
            logger.info("Worker queue has capacity (size {}). Resuming consumption.", queueSize);
            consumer.resume(consumer.assignment());
            paused = false;
        }
    }
    
    public void stop() {
        this.running = false;
    }
}
