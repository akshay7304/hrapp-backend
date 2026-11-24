package com.hrapp.security;

import com.hrapp.entity.User;
import com.hrapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access-token revocation persisted on {@code users.tokens_revoked_before}
 * and cached in-memory for the JVM. Survives restarts; safe for single-instance
 * Render deploys.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final Map<Long, Instant> cache = new ConcurrentHashMap<>();

    @Transactional
    public void revokeAllForUser(Long userId) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        cache.put(userId, now);
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokensRevokedBefore(LocalDateTime.ofInstant(now, IST));
            userRepository.save(user);
        });
    }

    @Transactional
    public void revokeAllForCompany(Long companyId) {
        if (companyId == null) {
            return;
        }
        userRepository.findByCompanyId(companyId).forEach(user -> revokeAllForUser(user.getId()));
    }

    public boolean isRevoked(Long userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        Instant cutoff = cache.computeIfAbsent(userId, this::loadCutoff);
        // EPOCH sentinel = no revocation recorded
        if (cutoff == null || Instant.EPOCH.equals(cutoff)) {
            return false;
        }
        return !issuedAt.isAfter(cutoff);
    }

    private Instant loadCutoff(Long userId) {
        return userRepository.findById(userId)
                .map(User::getTokensRevokedBefore)
                .map(ldt -> ldt.atZone(IST).toInstant())
                .orElse(Instant.EPOCH);
    }
}
