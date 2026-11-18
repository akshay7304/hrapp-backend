package com.hrapp.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Fails fast when the JWT signing secret is missing, too short, or still the
 * known local default while running with the {@code prod} profile.
 */
@Configuration
@Slf4j
public class JwtConfig {

    /** Known local-only placeholder — never allowed in production. */
    static final String WEAK_DEFAULT_SECRET =
            "hrapp-super-secret-key-change-this-in-production-min-256-bits";

    /** HS256 needs a key of at least 256 bits (32 bytes). */
    private static final int MIN_SECRET_BYTES = 32;

    private final Environment environment;

    @Value("${app.jwt.secret:}")
    private String secret;

    public JwtConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateJwtSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is missing. Set JWT_SECRET (or app.jwt.secret).");
        }

        int secretBytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + secretBytes
                            + " bytes). Use at least 32 bytes for HS256.");
        }

        boolean prod = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p));
        if (prod && WEAK_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "Refusing to start: production is using the known default JWT secret. "
                            + "Set a strong JWT_SECRET env var.");
        }

        if (!prod && WEAK_DEFAULT_SECRET.equals(secret)) {
            log.warn("Using the local default JWT secret — fine for local only, never for prod");
        }
    }
}
