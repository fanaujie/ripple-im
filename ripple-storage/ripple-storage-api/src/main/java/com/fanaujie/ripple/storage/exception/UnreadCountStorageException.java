package com.fanaujie.ripple.storage.exception;

/**
 * Base exception for unread count storage operations.
 * Extends RuntimeException as these are infrastructure failures.
 */
public class UnreadCountStorageException extends RuntimeException {

    public UnreadCountStorageException(String message) {
        super(message);
    }

    public UnreadCountStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
