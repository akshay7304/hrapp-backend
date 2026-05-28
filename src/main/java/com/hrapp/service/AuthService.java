package com.hrapp.service;

import com.hrapp.dto.request.ChangePasswordRequest;
import com.hrapp.dto.request.LoginRequest;
import com.hrapp.dto.request.RefreshTokenRequest;
import com.hrapp.dto.response.LoginResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.User;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.UserRepository;
import com.hrapp.repository.UserRoleRepository;
import com.hrapp.security.JwtUtil;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles login and refresh-token exchange. Both flows produce a brand new
 * access + refresh token pair. No tokens are persisted — JWTs are stateless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /**
     * Status names that block login. Keys are lower-cased for case-insensitive lookup.
     * Any status not present here is treated as login-eligible — so Active, Probation,
     * On Notice Period, and Contract all pass through.
     */
    private static final Map<String, String> BLOCKED_STATUS_MESSAGES = Map.of(
            "terminated", "Your account has been terminated",
            "resigned",   "Your account has been resigned",
            "suspended",  "Your account has been suspended",
            "inactive",   "Your account is inactive"
    );

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> {
                    log.warn("Login failed — mobile not registered: {}", request.getMobile());
                    return new ResourceNotFoundException("User not found");
                });

        ensureLoginAllowed(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed — invalid password for mobile: {}", request.getMobile());
            throw new UnauthorizedException("Invalid mobile or password");
        }

        List<String> roles = fetchRoleNames(user.getId());
        return buildLoginResponse(user, roles);
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        if (!jwtUtil.isRefreshToken(token)) {
            throw new BadRequestException("Provided token is not a refresh token");
        }

        String mobile = jwtUtil.extractMobile(token);
        Long userId = jwtUtil.extractUserId(token);

        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        if (userId != null && !userId.equals(user.getId())) {
            log.warn("Refresh token user mismatch — token userId={}, db userId={}", userId, user.getId());
            throw new UnauthorizedException("Refresh token does not match user");
        }

        ensureLoginAllowed(user);

        List<String> roles = fetchRoleNames(user.getId());
        return buildLoginResponse(user, roles);
    }

    /**
     * Changes the currently-authenticated user's password. The caller is taken
     * from the JWT-derived {@link SecurityUtil} rather than the request body so
     * a user can never change another user's password by spoofing an id.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            log.warn("Change-password failed — old password mismatch for userId={}", userId);
            throw new BadRequestException("Old password is incorrect");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }
        if (request.getNewPassword().equals(request.getOldPassword())) {
            throw new BadRequestException("New password must be different from old password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for userId={}", userId);
    }

    private void ensureLoginAllowed(User user) {
        String statusName = user.getStatus() != null ? user.getStatus().getName() : null;
        if (statusName == null) {
            log.warn("Login blocked — user {} has no status assigned", user.getMobile());
            throw new UnauthorizedException("Your account has no status — contact your administrator");
        }
        String blockedMessage = BLOCKED_STATUS_MESSAGES.get(statusName.trim().toLowerCase(Locale.ROOT));
        if (blockedMessage != null) {
            log.warn("Login blocked — user {} has status '{}'", user.getMobile(), statusName);
            throw new UnauthorizedException(blockedMessage);
        }
    }

    private List<String> fetchRoleNames(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getName())
                .toList();
    }

    private LoginResponse buildLoginResponse(User user, List<String> roles) {
        Company company = user.getCompany();
        Long companyId = company != null ? company.getId() : null;

        String accessToken = jwtUtil.generateAccessToken(
                user.getMobile(), user.getId(), companyId, roles);
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getMobile(), user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtUtil.getAccessTokenExpirationMs())
                .userId(user.getId())
                .companyId(companyId)
                .fullName(user.getFullName())
                .empCode(user.getEmpCode())
                .roles(roles)
                .build();
    }
}
