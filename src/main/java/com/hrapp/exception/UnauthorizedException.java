package com.hrapp.exception;

/**
 * Thrown when the caller is authenticated but lacks permission for the requested action
 * (e.g. employee trying to access another company's data).
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
