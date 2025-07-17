package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import java.time.Instant;


public class SnowflakeIdGenerator {
    //private static final int UNUSED_BITS = 1;
    private static final int TIMESTAMP_BITS = 41;
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long maxNodeId = (1L << NODE_ID_BITS) - 1;
    private static final long maxSequence = (1L << SEQUENCE_BITS) - 1;
    private static final long maxTimestamp = (1L << TIMESTAMP_BITS) - 1;

    //Date and time (GMT): Wednesday, January 1, 2025 0:00:00
    private static final long DEFAULT_EPOCH = 1735689600000L;

    private final long nodeId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long nodeId) {
        if(nodeId < 0 || nodeId > maxNodeId) {
            throw new IllegalArgumentException(String.format("NodeId must be between %d and %d", 0, maxNodeId));
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId()  {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - currentTimestamp));
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
        long adjustedTimestamp = currentTimestamp - DEFAULT_EPOCH;
        if (adjustedTimestamp > maxTimestamp) {
            throw new IllegalStateException("Timestamp overflow");
        }
        return (adjustedTimestamp << (NODE_ID_BITS + SEQUENCE_BITS)) |
               (nodeId << SEQUENCE_BITS) |
               sequence;
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
