package com.hrapp.exception;

/**
 * Thrown for client-side input errors that aren't caught by bean validation
 * (e.g. invalid state transitions, business-rule violations).
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
