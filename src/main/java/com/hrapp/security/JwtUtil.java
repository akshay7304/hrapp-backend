package com.hrapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Issues and validates stateless JWTs.
 * <p>
 * Two token types are issued:
 * <ul>
 *   <li><b>access</b> — short-lived, carries identity + roles, used on every request.</li>
 *   <li><b>refresh</b> — long-lived, carries only identity, exchanged at /auth/refresh-token.</li>
 * </ul>
 * The {@code type} claim distinguishes them so a refresh token can never be used
 * as an access token (and vice-versa).
 */
@Component
@Slf4j
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String secret;

    @Getter
    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Getter
    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    public String generateAccessToken(String mobile, Long userId, Long companyId, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .setSubject(mobile)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_COMPANY_ID, companyId)
                .claim(CLAIM_ROLES, roles)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String mobile, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .setSubject(mobile)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_USER_ID, userId)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractMobile(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object value = parseClaims(token).get(CLAIM_USER_ID);
        return value == null ? null : ((Number) value).longValue();
    }

    public Long extractCompanyId(String token) {
        Object value = parseClaims(token).get(CLAIM_COMPANY_ID);
        return value == null ? null : ((Number) value).longValue();
    }

    public List<String> extractRoles(String token) {
        Object value = parseClaims(token).get(CLAIM_ROLES);
        if (value instanceof List<?> list) {
            // Stream-map via toString() so the result is a typed List<String>
            // without an unchecked cast. For real String elements toString()
            // returns the same instance, so this is effectively a no-op copy.
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    public boolean isRefreshToken(String token) {
        Object value = parseClaims(token).get(CLAIM_TYPE);
        return TYPE_REFRESH.equals(value);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Malformed JWT: {}", ex.getMessage());
        } catch (SignatureException ex) {
            log.warn("Invalid JWT signature: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
