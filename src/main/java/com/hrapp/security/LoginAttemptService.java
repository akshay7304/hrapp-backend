package com.hrapp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-mobile login-attempt tracker. After
 * {@value #MAX_ATTEMPTS} consecutive failures the account is locked for
 * {@value #LOCK_DURATION_MINUTES} minutes; a successful login at any point
 * clears the counter.
 * <p>
 * State lives in a {@link ConcurrentHashMap} so it's safe under concurrent
 * logins on a single JVM, but it is <strong>not</strong> shared across
 * instances. In a horizontally-scaled deployment swap the storage out for
 * Redis (or equivalent) so locks survive failover.
 */
@Service
@Slf4j
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final int LOCK_DURATION_MINUTES = 30;

    private final ConcurrentHashMap<String, LoginAttemptInfo> attemptsByMobile = new ConcurrentHashMap<>();

    /**
     * @return {@code true} only if the account is currently within an active
     *         lock window. Expired locks are cleared as a side-effect.
     */
    public boolean isLocked(String mobile) {
        LoginAttemptInfo info = attemptsByMobile.get(mobile);
        if (info == null) {
            return false;
        }
        synchronized (info) {
            if (info.lockUntil == null) {
                return false;
            }
            if (info.lockUntil.isAfter(LocalDateTime.now())) {
                return true;
            }
            // Lock has expired — wipe the entry so the next failure starts fresh.
            attemptsByMobile.remove(mobile);
            return false;
        }
    }

    public void recordFailedAttempt(String mobile) {
        LoginAttemptInfo info = attemptsByMobile.computeIfAbsent(mobile, k -> new LoginAttemptInfo());
        synchronized (info) {
            info.attemptCount++;
            if (info.attemptCount >= MAX_ATTEMPTS) {
                info.lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
                log.warn("Account locked due to too many failed attempts: {}", mobile);
            }
        }
    }

    public void recordSuccessfulLogin(String mobile) {
        attemptsByMobile.remove(mobile);
    }

    public int getRemainingAttempts(String mobile) {
        LoginAttemptInfo info = attemptsByMobile.get(mobile);
        if (info == null) {
            return MAX_ATTEMPTS;
        }
        synchronized (info) {
            return Math.max(MAX_ATTEMPTS - info.attemptCount, 0);
        }
    }

    /**
     * Per-mobile counter. All mutations happen inside {@code synchronized(this)}
     * blocks held by {@link LoginAttemptService}, so plain fields are safe.
     */
    private static class LoginAttemptInfo {
        int attemptCount;
        LocalDateTime lockUntil;
    }
}
