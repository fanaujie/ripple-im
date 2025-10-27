package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class SnowflakeIdGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    // private static final int UNUSED_BITS = 1;
    private static final int TIMESTAMP_BITS = 41;
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long maxNodeId = (1L << NODE_ID_BITS) - 1;
    private static final long maxSequence = (1L << SEQUENCE_BITS) - 1;
    private static final long maxTimestamp = (1L << TIMESTAMP_BITS) - 1;

    // Date and time (GMT): Wednesday, January 1, 2025 0:00:00
    private static final long DEFAULT_EPOCH = 1735689600000L;

    private final long nodeId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long nodeId) {
        logger.debug("SnowflakeIdGenerator: Initializing with nodeId: {}", nodeId);
        if (nodeId < 0 || nodeId > maxNodeId) {
            logger.error(
                    "SnowflakeIdGenerator: Invalid nodeId: {}, must be between 0 and {}",
                    nodeId,
                    maxNodeId);
            throw new IllegalArgumentException(
                    String.format("NodeId must be between %d and %d", 0, maxNodeId));
        }
        this.nodeId = nodeId;
        logger.debug("SnowflakeIdGenerator: Initialization complete for nodeId: {}", nodeId);
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();
        logger.debug(
                "nextId: Current timestamp: {}, lastTimestamp: {}, sequence: {}",
                currentTimestamp,
                lastTimestamp,
                sequence);

        if (currentTimestamp < lastTimestamp) {
            logger.error(
                    "nextId: Clock moved backwards. Current: {}, Last: {}, Difference: {} ms",
                    currentTimestamp,
                    lastTimestamp,
                    lastTimestamp - currentTimestamp);
            throw new IllegalStateException(
                    String.format(
                            "Clock moved backwards. Refusing to generate id for %d milliseconds",
                            lastTimestamp - currentTimestamp));
        }
        if (currentTimestamp == lastTimestamp) {
            // Same millisecond, increment sequence
            sequence = (sequence + 1) & maxSequence;
            logger.debug("nextId: Same millisecond, incremented sequence to: {}", sequence);
            if (sequence == 0L) {
                // Sequence overflow, wait for next millisecond
                logger.debug("nextId: Sequence overflow detected, waiting for next millisecond");
                currentTimestamp = waitNextMillis(lastTimestamp);
                logger.debug("nextId: Got next millisecond: {}", currentTimestamp);
            }
        } else {
            // New millisecond, reset sequence
            sequence = 0L;
            logger.debug("nextId: New millisecond detected, reset sequence to 0");
        }
        lastTimestamp = currentTimestamp;
        long adjustedTimestamp = currentTimestamp - DEFAULT_EPOCH;
        logger.debug("nextId: Adjusted timestamp: {}", adjustedTimestamp);
        if (adjustedTimestamp > maxTimestamp) {
            logger.error(
                    "nextId: Timestamp overflow - adjustedTimestamp: {} exceeds maxTimestamp: {}",
                    adjustedTimestamp,
                    maxTimestamp);
            throw new IllegalStateException("Timestamp overflow");
        }
        long id =
                (adjustedTimestamp << (NODE_ID_BITS + SEQUENCE_BITS))
                        | (nodeId << SEQUENCE_BITS)
                        | sequence;
        logger.debug(
                "nextId: Generated snowflake ID: {} (timestamp: {}, nodeId: {}, sequence: {})",
                id,
                adjustedTimestamp,
                nodeId,
                sequence);
        return id;
    }

    private long waitNextMillis(long lastTimestamp) {
        logger.debug("waitNextMillis: Waiting for next millisecond after: {}", lastTimestamp);
        long timestamp = timestamp();
        long waitCount = 0;
        while (timestamp <= lastTimestamp) {
            timestamp = timestamp();
            waitCount++;
        }
        logger.debug(
                "waitNextMillis: Wait completed after {} iterations, got timestamp: {}",
                waitCount,
                timestamp);
        return timestamp;
    }

    private long timestamp() {
        long ts = Instant.now().toEpochMilli();
        logger.trace("timestamp: Current epoch millis: {}", ts);
        return ts;
    }
}
