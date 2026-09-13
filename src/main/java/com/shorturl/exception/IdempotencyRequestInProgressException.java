package com.shorturl.exception;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException(String message) {
        super(message);
    }
}
