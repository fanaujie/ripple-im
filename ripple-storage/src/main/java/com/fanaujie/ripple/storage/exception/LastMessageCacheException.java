package com.fanaujie.ripple.storage.exception;

/**
 * Exception thrown when Redis cache operations fail for last message.
 */
public class LastMessageCacheException extends LastMessageStorageException {

    public LastMessageCacheException(String message) {
        super(message);
    }

    public LastMessageCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
