package com.hrapp.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory access-token revocation for a single JVM (Render/local).
 * After password change or account termination, tokens issued earlier
 * are rejected until expiry naturally drops them from clients.
 */
@Service
public class TokenRevocationService {

    private final Map<Long, Instant> revokedBeforeByUserId = new ConcurrentHashMap<>();

    public void revokeAllForUser(Long userId) {
        if (userId != null) {
            revokedBeforeByUserId.put(userId, Instant.now());
        }
    }

    public boolean isRevoked(Long userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        Instant cutoff = revokedBeforeByUserId.get(userId);
        return cutoff != null && !issuedAt.isAfter(cutoff);
    }
}
