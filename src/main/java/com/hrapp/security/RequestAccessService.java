package com.hrapp.security;

import com.hrapp.entity.User;
import com.hrapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-request auth gate with a short in-memory cache so every API call does not
 * hit Supabase for user status / company active / token revocation.
 * <p>
 * Cache TTL is short (45s) so status/revoke changes still take effect quickly
 * without paying 1–3 RTTs on every request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestAccessService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long TTL_MS = 45_000L;

    private final UserRepository userRepository;
    private final AccountAccessService accountAccessService;

    private final ConcurrentHashMap<Long, CachedAccess> cache = new ConcurrentHashMap<>();

    /**
     * @return denial message if the token must be rejected; empty if OK
     */
    @Transactional(readOnly = true)
    public Optional<String> denyReason(Long userId, Instant tokenIssuedAt) {
        if (userId == null) {
            return Optional.of("Not authenticated");
        }
        CachedAccess snap = cache.compute(userId, (id, existing) -> {
            long now = System.currentTimeMillis();
            if (existing != null && (now - existing.loadedAtMs()) < TTL_MS) {
                return existing;
            }
            return load(id, now);
        });

        if (snap.missing()) {
            return Optional.of("User not found");
        }
        if (tokenIssuedAt != null
                && snap.tokensRevokedBefore() != null
                && !Instant.EPOCH.equals(snap.tokensRevokedBefore())
                && !tokenIssuedAt.isAfter(snap.tokensRevokedBefore())) {
            return Optional.of("Session revoked — please login again");
        }
        return snap.deniedReason();
    }

    public void invalidate(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void invalidateAll() {
        cache.clear();
    }

    private CachedAccess load(Long userId, long loadedAtMs) {
        Optional<User> opt = userRepository.findAccessSnapshotById(userId);
        if (opt.isEmpty()) {
            return new CachedAccess(true, Instant.EPOCH, Optional.of("User not found"), loadedAtMs);
        }
        User user = opt.get();
        Instant cutoff = Instant.EPOCH;
        LocalDateTime revoked = user.getTokensRevokedBefore();
        if (revoked != null) {
            cutoff = revoked.atZone(IST).toInstant();
        }
        Optional<String> denied = accountAccessService.accessDeniedReason(user);
        return new CachedAccess(false, cutoff, denied, loadedAtMs);
    }

    private record CachedAccess(
            boolean missing,
            Instant tokensRevokedBefore,
            Optional<String> deniedReason,
            long loadedAtMs
    ) {
    }
}
