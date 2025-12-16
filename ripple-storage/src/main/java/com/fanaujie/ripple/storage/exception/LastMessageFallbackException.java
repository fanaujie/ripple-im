package com.fanaujie.ripple.storage.exception;

/**
 * Exception thrown when Cassandra fallback fails after Redis cache miss/failure.
 */
public class LastMessageFallbackException extends LastMessageStorageException {

    public LastMessageFallbackException(String message) {
        super(message);
    }

    public LastMessageFallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
