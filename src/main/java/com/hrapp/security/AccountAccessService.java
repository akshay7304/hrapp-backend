package com.hrapp.security;

import com.hrapp.entity.Company;
import com.hrapp.entity.User;
import com.hrapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared login / request-time access rules for employment status and company active flag.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountAccessService {

    private static final Map<String, String> BLOCKED_STATUS_MESSAGES = Map.of(
            "terminated", "Your account has been terminated",
            "resigned", "Your account has been resigned",
            "suspended", "Your account has been suspended",
            "inactive", "Your account is inactive"
    );

    /** Statuses excluded from payroll and attendance reports. */
    public static final Set<String> WORKFORCE_EXCLUDED_STATUSES = Set.of(
            "Terminated", "Resigned", "Suspended", "Inactive");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<String> accessDeniedReason(Long userId) {
        if (userId == null) {
            return Optional.of("Not authenticated");
        }
        return userRepository.findById(userId)
                .map(this::accessDeniedReason)
                .orElse(Optional.of("User not found"));
    }

    public Optional<String> accessDeniedReason(User user) {
        if (user == null) {
            return Optional.of("User not found");
        }
        Company company = user.getCompany();
        if (company != null && Boolean.FALSE.equals(company.getIsActive())) {
            return Optional.of("Your company account is inactive — contact support");
        }
        String statusName = user.getStatus() != null ? user.getStatus().getName() : null;
        if (statusName == null || statusName.isBlank()) {
            return Optional.of("Your account has no status — contact your administrator");
        }
        String blocked = BLOCKED_STATUS_MESSAGES.get(statusName.trim().toLowerCase(Locale.ROOT));
        return Optional.ofNullable(blocked);
    }

    public boolean isAccessAllowed(Long userId) {
        return accessDeniedReason(userId).isEmpty();
    }
}
