package com.hrapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Reads {@code Authorization: Bearer <token>}, validates it, checks revocation
 * and live account/company status, then populates {@link SecurityContextHolder}.
 * Refresh tokens are rejected for normal API calls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RequestAccessService requestAccessService;

    /**
     * server.servlet.context-path is {@code /api/v1}, so the servlet path here
     * is the URL <em>after</em> that prefix (e.g. {@code /auth/login}).
     * <p>
     * Only public auth endpoints skip JWT parsing. Authenticated auth routes
     * such as {@code /auth/change-password} must still run through this filter.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/health")
                || path.equals("/auth/login")
                || path.equals("/auth/refresh-token")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)
                && jwtUtil.validateToken(token)
                && !jwtUtil.isRefreshToken(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = jwtUtil.extractUserId(token);
                Optional<String> denied = requestAccessService.denyReason(
                        userId, jwtUtil.extractIssuedAt(token));
                if (denied.isPresent()) {
                    log.debug("Rejected JWT for userId={} — {}", userId, denied.get());
                    writeUnauthorized(response, denied.get());
                    return;
                }

                AuthenticatedUser principal = new AuthenticatedUser(
                        userId,
                        jwtUtil.extractCompanyId(token),
                        jwtUtil.extractMobile(token),
                        jwtUtil.extractRoles(token)
                );

                List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                log.warn("Failed to set authentication from JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message == null ? "Unauthorized" : message.replace("\"", "'");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + safe + "\",\"data\":null,\"errors\":null}");
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
