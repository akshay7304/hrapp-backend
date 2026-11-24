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
import com.hrapp.security.AccountAccessService;
import com.hrapp.security.JwtUtil;
import com.hrapp.security.LoginAttemptService;
import com.hrapp.security.SecurityUtil;
import com.hrapp.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles login and refresh-token exchange. Both flows produce a brand new
 * access + refresh token pair. No tokens are persisted — JWTs are stateless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final TokenRevocationService tokenRevocationService;
    private final AccountAccessService accountAccessService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String mobile = request.getMobile();

        if (loginAttemptService.isLocked(mobile)) {
            log.warn("Login blocked — account already locked for mobile={}", mobile);
            throw new UnauthorizedException(
                    "Account temporarily locked due to too many failed attempts. "
                            + "Try again after 30 minutes.");
        }

        User user = userRepository.findByMobile(mobile).orElse(null);
        if (user == null) {
            // Same failure path as a wrong password so callers cannot tell
            // whether the mobile is registered. Still counts toward lockout
            // for this mobile key (harmless for unknown numbers).
            log.warn("Login failed — mobile not registered: {}", mobile);
            loginAttemptService.recordFailedAttempt(mobile);
            throwInvalidCredentials(mobile);
        }

        ensureLoginAllowed(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed — invalid password for mobile: {}", mobile);
            loginAttemptService.recordFailedAttempt(mobile);
            throwInvalidCredentials(mobile);
        }

        loginAttemptService.recordSuccessfulLogin(mobile);

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

        // Password change / status revoke must invalidate outstanding refresh
        // tokens the same way the filter invalidates access tokens.
        if (userId != null
                && tokenRevocationService.isRevoked(userId, jwtUtil.extractIssuedAt(token))) {
            throw new UnauthorizedException("Refresh token has been revoked");
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
        tokenRevocationService.revokeAllForUser(userId);
        log.info("Password changed for userId={}", userId);
    }

    private void throwInvalidCredentials(String mobile) {
        int remaining = loginAttemptService.getRemainingAttempts(mobile);
        if (remaining > 0) {
            throw new UnauthorizedException(
                    "Invalid mobile or password. " + remaining + " attempts remaining.");
        }
        throw new UnauthorizedException(
                "Account temporarily locked due to too many failed attempts. "
                        + "Try again after 30 minutes.");
    }

    private void ensureLoginAllowed(User user) {
        accountAccessService.accessDeniedReason(user).ifPresent(msg -> {
            log.warn("Login blocked — user {} — {}", user.getMobile(), msg);
            throw new UnauthorizedException(msg);
        });
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
