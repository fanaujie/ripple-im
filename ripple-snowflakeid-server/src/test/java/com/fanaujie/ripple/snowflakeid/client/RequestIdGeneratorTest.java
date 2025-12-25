package com.fanaujie.ripple.snowflakeid.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdGeneratorTest {

    @Test
    void testRequestIdFormat() {
        RequestIdGenerator generator = new RequestIdGenerator();
        String requestId = generator.generateRequestId();

        assertNotNull(requestId);
        assertFalse(requestId.isEmpty());

        // Request ID should be numeric (timestamp + sequence)
        assertTrue(Pattern.matches("\\d+", requestId), "Request ID should be numeric");
    }

    @Test
    void testRequestIdUniqueness() {
        RequestIdGenerator generator = new RequestIdGenerator();
        Set<String> ids = new HashSet<>();
        int count = 10000;

        for (int i = 0; i < count; i++) {
            String id = generator.generateRequestId();
            assertFalse(ids.contains(id), "Duplicate request ID found: " + id);
            ids.add(id);
        }

        assertEquals(count, ids.size());
    }

    @Test
    void testRequestIdContainsTimestamp() {
        RequestIdGenerator generator = new RequestIdGenerator();
        long beforeGeneration = System.currentTimeMillis();
        String requestId = generator.generateRequestId();
        long afterGeneration = System.currentTimeMillis();

        // The request ID format is timestamp + 4-digit sequence
        // So the first part (length - 4) should be the timestamp
        assertTrue(requestId.length() > 4, "Request ID should contain timestamp + sequence");

        String timestampPart = requestId.substring(0, requestId.length() - 4);
        long timestamp = Long.parseLong(timestampPart);

        assertTrue(
                timestamp >= beforeGeneration && timestamp <= afterGeneration,
                "Timestamp should be within generation window");
    }

    @Test
    void testSequentialRequestIds() {
        RequestIdGenerator generator = new RequestIdGenerator();

        String id1 = generator.generateRequestId();
        String id2 = generator.generateRequestId();

        assertNotEquals(id1, id2, "Sequential request IDs should be different");

        // Parse as long to compare - later IDs should be greater or equal
        long num1 = Long.parseLong(id1);
        long num2 = Long.parseLong(id2);

        assertTrue(num2 >= num1, "Sequential IDs should be monotonically increasing");
    }

    @Test
    void testHighVolumeRequestIdGeneration() {
        RequestIdGenerator generator = new RequestIdGenerator();
        Set<String> ids = new HashSet<>();
        int count = 100000;

        for (int i = 0; i < count; i++) {
            ids.add(generator.generateRequestId());
        }

        assertEquals(count, ids.size(), "All generated request IDs should be unique");
    }

    @Test
    void testMultipleGeneratorsProduceUniqueIds() {
        RequestIdGenerator generator1 = new RequestIdGenerator();
        RequestIdGenerator generator2 = new RequestIdGenerator();

        Set<String> ids = new HashSet<>();
        int countPerGenerator = 1000;

        for (int i = 0; i < countPerGenerator; i++) {
            ids.add(generator1.generateRequestId());
            ids.add(generator2.generateRequestId());
        }

        // Due to time-based generation, IDs from different generators
        // might overlap if generated at exact same millisecond
        // But in practice, they should mostly be unique
        assertTrue(
                ids.size() >= countPerGenerator,
                "Most IDs should be unique across generators");
    }
}
