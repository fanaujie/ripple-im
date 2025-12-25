package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1; // 1023

    @Test
    void testValidNodeIdRange_MinValue() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0);
        long id = generator.nextId();
        assertTrue(id > 0);
    }

    @Test
    void testValidNodeIdRange_MaxValue() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(MAX_NODE_ID);
        long id = generator.nextId();
        assertTrue(id > 0);
    }

    @Test
    void testValidNodeIdRange_MiddleValue() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(512);
        long id = generator.nextId();
        assertTrue(id > 0);
    }

    @Test
    void testInvalidNodeId_Negative() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertTrue(exception.getMessage().contains("NodeId must be between"));
    }

    @Test
    void testInvalidNodeId_ExceedsMax() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> new SnowflakeIdGenerator(MAX_NODE_ID + 1));
        assertTrue(exception.getMessage().contains("NodeId must be between"));
    }

    @Test
    void testIdUniqueness() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        int count = 10000;

        for (int i = 0; i < count; i++) {
            long id = generator.nextId();
            assertFalse(ids.contains(id), "Duplicate ID found: " + id);
            ids.add(id);
        }

        assertEquals(count, ids.size());
    }

    @Test
    void testIdIncrementsMonotonically() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        long previousId = generator.nextId();

        for (int i = 0; i < 1000; i++) {
            long currentId = generator.nextId();
            assertTrue(currentId > previousId, "ID should be monotonically increasing");
            previousId = currentId;
        }
    }

    @Test
    void testSequenceIncrementsInSameMillisecond() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);

        // Generate many IDs quickly to ensure some are in the same millisecond
        long id1 = generator.nextId();
        long id2 = generator.nextId();

        // Extract sequence from IDs (last 12 bits)
        long sequence1 = id1 & ((1L << SEQUENCE_BITS) - 1);
        long sequence2 = id2 & ((1L << SEQUENCE_BITS) - 1);

        // If in same millisecond, sequence2 should be sequence1 + 1
        // If in different millisecond, sequence2 should be 0
        assertTrue(
                sequence2 == sequence1 + 1 || sequence2 == 0,
                "Sequence should increment or reset to 0");
    }

    @Test
    void testNodeIdEncodedInId() {
        long nodeId = 123;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(nodeId);
        long id = generator.nextId();

        // Extract node ID from generated ID
        // Node ID is in bits 12-21 (after sequence bits, before timestamp)
        long extractedNodeId = (id >> SEQUENCE_BITS) & ((1L << NODE_ID_BITS) - 1);

        assertEquals(nodeId, extractedNodeId, "Node ID should be correctly encoded in the ID");
    }

    @Test
    void testDifferentNodeIdsProduceDifferentIds() {
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(1);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(2);

        long id1 = generator1.nextId();
        long id2 = generator2.nextId();

        assertNotEquals(id1, id2, "Different node IDs should produce different IDs");

        // Verify node IDs are different in the generated IDs
        long extractedNodeId1 = (id1 >> SEQUENCE_BITS) & ((1L << NODE_ID_BITS) - 1);
        long extractedNodeId2 = (id2 >> SEQUENCE_BITS) & ((1L << NODE_ID_BITS) - 1);

        assertEquals(1, extractedNodeId1);
        assertEquals(2, extractedNodeId2);
    }

    @Test
    void testHighVolumeIdGeneration() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        int count = 100000;

        for (int i = 0; i < count; i++) {
            ids.add(generator.nextId());
        }

        assertEquals(count, ids.size(), "All generated IDs should be unique");
    }
}
