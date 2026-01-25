package com.fanaujie.ripple.webhookservice.http;

import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SSEEventParserTest {

    private SSEEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new SSEEventParser();
    }

    @Nested
    class DeltaEventTests {

        @Test
        void parseDeltaEvent_WithContent_ReturnsDeltaEvent() {
            // Given
            String eventType = "delta";
            String data = "{\"content\": \"Hello\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isDelta());
            assertEquals("Hello", event.getContent());
        }

        @Test
        void parseDeltaEvent_WithEmptyContent_ReturnsEmptyDelta() {
            // Given
            String eventType = "delta";
            String data = "{\"content\": \"\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isDelta());
            assertEquals("", event.getContent());
        }

        @Test
        void parseDeltaEvent_WithMissingContentField_ReturnsEmptyContent() {
            // Given
            String eventType = "delta";
            String data = "{\"other_field\": \"value\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isDelta());
            assertEquals("", event.getContent());
        }

        @Test
        void parseDeltaEvent_CaseInsensitive_ParsesCorrectly() {
            // Given
            String eventType = "DELTA";
            String data = "{\"content\": \"Test\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isDelta());
            assertEquals("Test", event.getContent());
        }
    }

    @Nested
    class DoneEventTests {

        @Test
        void parseDoneEvent_WithFullText_ReturnsDoneEvent() {
            // Given
            String eventType = "done";
            String data = "{\"full_text\": \"Hello World\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isDone());
            assertEquals("Hello World", event.getFullText());
        }

        @Test
        void parseDoneEvent_WithMissingFullTextField_ReturnsEmptyFullText() {
            // Given
            String eventType = "done";
            String data = "{\"other_field\": \"value\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isDone());
            assertEquals("", event.getFullText());
        }
    }

    @Nested
    class ErrorEventTests {

        @Test
        void parseErrorEvent_WithErrorMessage_ReturnsErrorEvent() {
            // Given
            String eventType = "error";
            String data = "{\"error\": \"Rate limited\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isError());
            assertEquals("Rate limited", event.getErrorMessage());
        }

        @Test
        void parseErrorEvent_WithMissingErrorField_ReturnsUnknownError() {
            // Given
            String eventType = "error";
            String data = "{\"other_field\": \"value\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isError());
            assertEquals("Unknown error", event.getErrorMessage());
        }
    }

    @Nested
    class MalformedInputTests {

        @Test
        void parseMalformedJson_ReturnsErrorEvent() {
            // Given
            String eventType = "delta";
            String data = "not valid json {";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNotNull(event);
            assertTrue(event.isError());
            assertTrue(event.getErrorMessage().startsWith("Failed to parse response:"));
        }

        @Test
        void parseNullData_ReturnsNull() {
            // Given
            String eventType = "delta";

            // When
            SSEEvent event = parser.parse(eventType, null);

            // Then
            assertNull(event);
        }

        @Test
        void parseEmptyData_ReturnsNull() {
            // Given
            String eventType = "delta";
            String data = "";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNull(event);
        }

        @Test
        void parseUnknownEventType_ReturnsNull() {
            // Given
            String eventType = "unknown_type";
            String data = "{\"content\": \"test\"}";

            // When
            SSEEvent event = parser.parse(eventType, data);

            // Then
            assertNull(event);
        }
    }
}
