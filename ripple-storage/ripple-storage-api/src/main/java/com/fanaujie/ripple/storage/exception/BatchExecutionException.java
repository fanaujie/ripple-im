package com.fanaujie.ripple.storage.exception;

/**
 * Exception thrown when batch execution fails.
 */
public class BatchExecutionException extends RuntimeException {

    public BatchExecutionException(String message) {
        super(message);
    }

    public BatchExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
