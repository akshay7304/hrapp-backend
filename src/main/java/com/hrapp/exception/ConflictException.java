package com.hrapp.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown when a write would violate a uniqueness or state-conflict rule
 * (e.g. mobile already registered, leave already approved).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 * <p>
 * Optional {@link #getFieldErrors()} lets the web client highlight the
 * conflicting input (e.g. {@code adminMobile}).
 */
public class ConflictException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ConflictException(String message) {
        this(message, Collections.emptyMap());
    }

    public ConflictException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? Map.copyOf(fieldErrors) : Map.of();
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
        this.fieldErrors = Map.of();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
