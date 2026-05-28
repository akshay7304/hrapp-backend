package com.hrapp.exception;

/**
 * Thrown when a write would violate a uniqueness or state-conflict rule
 * (e.g. mobile already registered, leave already approved).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
