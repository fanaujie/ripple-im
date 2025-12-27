package com.fanaujie.ripple.snowflakeid.client;

import java.time.Instant;

public class RequestIdGenerator {
    private static final int SEQUENCE_BITS = 12;
    private static final long maxSequence = (1L << SEQUENCE_BITS) - 1;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public synchronized String generateRequestId() {
        long currentTimestamp = timestamp();
        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(
                    String.format(
                            "Clock moved backwards. Refusing to generate id for %d milliseconds",
                            lastTimestamp - currentTimestamp));
        }
        if (currentTimestamp == lastTimestamp) {
            // Same millisecond, increment sequence
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0L) {
                // Sequence overflow, wait for next millisecond
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond, reset sequence
            sequence = 0L;
        }
        lastTimestamp = currentTimestamp;
        return String.format("%d%04d", currentTimestamp, sequence);
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = timestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = timestamp();
        }
        return timestamp;
    }

    private long timestamp() {
        return Instant.now().toEpochMilli();
    }
}
