package com.hrapp.security;

import java.util.List;

/**
 * Lightweight principal stored in the {@link org.springframework.security.core.context.SecurityContext}
 * after a valid JWT has been parsed.
 * <p>
 * Carries the same identity claims that {@code JwtUtil} bakes into the access
 * token — accessible from anywhere via {@link SecurityUtil}.
 *
 * @param userId    DB id of the user.
 * @param companyId tenant id; {@code null} for SUPERADMIN.
 * @param mobile    JWT subject — also the user's login id.
 * @param roles     role names without the {@code ROLE_} prefix (e.g. {@code "ADMIN"}).
 */
public record AuthenticatedUser(
        Long userId,
        Long companyId,
        String mobile,
        List<String> roles
) {
}
