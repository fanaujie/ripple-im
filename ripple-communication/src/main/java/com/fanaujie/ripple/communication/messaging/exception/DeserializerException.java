package com.fanaujie.ripple.communication.messaging.exception;

public class DeserializerException extends RuntimeException {
    public DeserializerException(String message) {
        super(message);
    }

    public DeserializerException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeserializerException(Throwable cause) {
        super(cause);
    }
}
