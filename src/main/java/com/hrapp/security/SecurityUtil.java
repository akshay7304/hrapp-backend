package com.hrapp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * Static accessor for the currently-authenticated user's JWT-derived identity.
 * <p>
 * Reads from {@link SecurityContextHolder}, which is populated by
 * {@code JwtAuthenticationFilter} on each request. Returns {@code null} (or
 * empty list) when nobody is authenticated — call sites are expected to
 * decide how to react.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long getCurrentUserId() {
        AuthenticatedUser user = currentUser();
        return user == null ? null : user.userId();
    }

    public static Long getCurrentUserCompanyId() {
        AuthenticatedUser user = currentUser();
        return user == null ? null : user.companyId();
    }

    public static List<String> getCurrentUserRoles() {
        AuthenticatedUser user = currentUser();
        if (user == null || user.roles() == null) {
            return Collections.emptyList();
        }
        return user.roles();
    }

    public static boolean hasRole(String role) {
        return getCurrentUserRoles().contains(role);
    }

    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : null;
    }
}
