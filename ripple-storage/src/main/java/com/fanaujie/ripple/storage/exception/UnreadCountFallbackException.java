package com.fanaujie.ripple.storage.exception;

/**
 * Exception thrown when Cassandra fallback fails after Redis cache miss/failure.
 */
public class UnreadCountFallbackException extends UnreadCountStorageException {

    public UnreadCountFallbackException(String message) {
        super(message);
    }

    public UnreadCountFallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
