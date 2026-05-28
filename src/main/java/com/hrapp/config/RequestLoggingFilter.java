package com.hrapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Access-log style request logger.
 * <p>
 * Logs one line per request after the response has been written:
 * <pre>
 *   [POST] /api/v1/auth/login → 200 (45ms)
 * </pre>
 * Registered at {@link Ordered#HIGHEST_PRECEDENCE} so it sits <em>outside</em>
 * the Spring Security filter chain — that way 401 / 403 responses generated
 * by {@code JwtAuthenticationEntryPoint} and {@code JwtAccessDeniedHandler}
 * still get logged with the real status code.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/swagger-ui") || uri.contains("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - start;
            log.info("[{}] {} → {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs);
        }
    }
}
