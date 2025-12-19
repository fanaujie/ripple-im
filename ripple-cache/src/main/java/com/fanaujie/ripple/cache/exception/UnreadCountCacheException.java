package com.fanaujie.ripple.cache.exception;

import com.fanaujie.ripple.storage.exception.UnreadCountStorageException;

/**
 * Exception thrown when Redis cache operations fail for unread count.
 */
public class UnreadCountCacheException extends UnreadCountStorageException {

    public UnreadCountCacheException(String message) {
        super(message);
    }

    public UnreadCountCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
