package com.fanaujie.ripple.cache.exception;

import com.fanaujie.ripple.storage.exception.LastMessageStorageException;

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
