package com.hrapp.service;

import com.hrapp.dto.request.AssignAdminRequest;
import com.hrapp.dto.response.EmployeeResponse;
import com.hrapp.entity.Role;
import com.hrapp.entity.User;
import com.hrapp.entity.UserRole;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.RoleRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.repository.UserRoleRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Multi-admin support per company: list / assign / revoke the {@code ADMIN} role.
 * Companies may have multiple admins; the last admin cannot be revoked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyAdminService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";
    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeService employeeService;

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listAdmins(Long companyId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        return userRepository.findByCompanyIdAndRoleName(effectiveCompanyId, ROLE_ADMIN).stream()
                .map(u -> employeeService.getEmployeeById(u.getId()))
                .toList();
    }

    @Transactional
    public EmployeeResponse assignAdmin(Long companyId, AssignAdminRequest request) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureUserInCompany(user, effectiveCompanyId);

        if (userRoleRepository.existsByUser_IdAndRole_Name(user.getId(), ROLE_ADMIN)) {
            throw new ConflictException(
                    "User is already a company admin",
                    Map.of("userId", "Already an admin"));
        }
        if (userRoleRepository.existsByUser_IdAndRole_Name(user.getId(), ROLE_SUPERADMIN)) {
            throw new BadRequestException("Cannot change SUPERADMIN via company admin APIs");
        }

        replacePrimaryRole(user, ROLE_ADMIN);
        log.info("Assigned ADMIN to user={} company={}", user.getId(), effectiveCompanyId);
        return employeeService.getEmployeeById(user.getId());
    }

    @Transactional
    public EmployeeResponse revokeAdmin(Long companyId, Long userId) {
        Long effectiveCompanyId = resolveCompanyId(companyId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureUserInCompany(user, effectiveCompanyId);

        if (!userRoleRepository.existsByUser_IdAndRole_Name(user.getId(), ROLE_ADMIN)) {
            throw new BadRequestException("User is not a company admin");
        }

        List<User> admins = userRepository.findByCompanyIdAndRoleName(effectiveCompanyId, ROLE_ADMIN);
        if (admins.size() <= 1) {
            throw new BadRequestException("Cannot revoke the last company admin");
        }

        replacePrimaryRole(user, ROLE_EMPLOYEE);
        log.info("Revoked ADMIN from user={} company={} (downgraded to EMPLOYEE)",
                user.getId(), effectiveCompanyId);
        return employeeService.getEmployeeById(user.getId());
    }

    private void replacePrimaryRole(User user, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BadRequestException("Role '" + roleName + "' is not configured"));
        userRoleRepository.deleteByUser_Id(user.getId());
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
    }

    /**
     * ADMIN may only manage their own company. SUPERADMIN may pass any
     * {@code companyId}. Path companyId is required for SUPERADMIN.
     */
    private Long resolveCompanyId(Long pathCompanyId) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            if (pathCompanyId == null) {
                throw new BadRequestException("companyId is required");
            }
            companyRepository.findById(pathCompanyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
            return pathCompanyId;
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        if (callerCompanyId == null) {
            throw new UnauthorizedException("Caller is not bound to a company");
        }
        if (pathCompanyId != null && !pathCompanyId.equals(callerCompanyId)) {
            throw new UnauthorizedException("Cannot manage admins for another company");
        }
        return callerCompanyId;
    }

    private void ensureUserInCompany(User user, Long companyId) {
        Long userCompanyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (!companyId.equals(userCompanyId)) {
            throw new UnauthorizedException("Employee does not belong to this company");
        }
    }
}
