package com.fanaujie.ripple.communication.messaging.exception;

public class ProducerException extends RuntimeException {
    public ProducerException(String message) {
        super(message);
    }

    public ProducerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProducerException(Throwable cause) {
        super(cause);
    }
}
