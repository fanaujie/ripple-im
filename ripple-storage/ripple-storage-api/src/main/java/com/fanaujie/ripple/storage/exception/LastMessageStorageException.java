package com.fanaujie.ripple.storage.exception;

/**
 * Base exception for last message storage operations.
 * Extends RuntimeException as these are infrastructure failures.
 */
public class LastMessageStorageException extends RuntimeException {

    public LastMessageStorageException(String message) {
        super(message);
    }

    public LastMessageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
