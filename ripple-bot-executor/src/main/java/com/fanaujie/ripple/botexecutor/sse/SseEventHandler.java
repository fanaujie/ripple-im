package com.fanaujie.ripple.botexecutor.sse;

/**
 * Handler for SSE events during streaming.
 */
public interface SseEventHandler {
    /**
     * Called when a data chunk is received.
     * @param chunk The chunk content (without "data: " prefix)
     */
    void onChunk(String chunk);

    /**
     * Called when the stream is complete.
     * @param fullContent The accumulated full content
     */
    void onComplete(String fullContent);

    /**
     * Called when an error occurs during streaming.
     * @param error The error that occurred
     */
    void onError(Throwable error);
}
