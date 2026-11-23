package com.hrapp.service;

import com.hrapp.dto.request.CreateEmployeeRequest;
import com.hrapp.dto.request.UpdateEmployeeRequest;
import com.hrapp.dto.request.UpdateEmployeeStatusRequest;
import com.hrapp.dto.response.EmployeeResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Department;
import com.hrapp.entity.Designation;
import com.hrapp.entity.Role;
import com.hrapp.entity.User;
import com.hrapp.entity.UserRole;
import com.hrapp.entity.UserStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.DepartmentRepository;
import com.hrapp.repository.DesignationRepository;
import com.hrapp.repository.RoleRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.repository.UserRoleRepository;
import com.hrapp.repository.UserStatusRepository;
import com.hrapp.security.SecurityUtil;
import com.hrapp.security.TokenRevocationService;
import com.hrapp.util.TempPasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Employee CRUD bounded by the caller's company.
 * <p>
 * Multi-tenant rule: every read/write filters by the logged-in user's
 * {@code companyId}. SUPERADMIN bypasses the company filter on reads but
 * is not expected to create employees (the controller restricts create/update
 * to ADMIN/HR via {@code @PreAuthorize}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private static final String STATUS_ACTIVE = "Active";
    private static final String ROLE_SUPERADMIN = "SUPERADMIN";
    private static final Set<String> TOKEN_REVOKE_STATUSES =
            Set.of("terminated", "resigned", "suspended", "inactive");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final UserStatusRepository userStatusRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeaveService leaveService;
    private final TokenRevocationService tokenRevocationService;
    private final RoleService roleService;

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        Long companyId = requireCallerCompanyId();

        if (userRepository.existsByMobile(request.getMobile())) {
            throw new ConflictException(
                    "Mobile number is already registered",
                    Map.of("mobile", "Mobile number is already registered"));
        }

        String empCode = resolveEmpCode(request.getEmpCode(), companyId);

        Department department = departmentRepository
                .findByIdAndCompanyId(request.getDepartmentId(), companyId)
                .orElseThrow(() -> new BadRequestException("Department does not belong to your company"));
        Designation designation = designationRepository
                .findByIdAndCompanyId(request.getDesignationId(), companyId)
                .orElseThrow(() -> new BadRequestException("Designation does not belong to your company"));

        UserStatus activeStatus = userStatusRepository.findByName(STATUS_ACTIVE)
                .orElseThrow(() -> new BadRequestException("'" + STATUS_ACTIVE + "' status is not configured"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        roleService.ensureAssignable(role);

        Company company = companyRepository.findByIdAndIsActive(companyId, true)
                .orElseThrow(() -> new BadRequestException("Company is inactive or does not exist"));

        String temporaryPassword = TempPasswordUtil.generate(10);
        User user = User.builder()
                .company(company)
                .department(department)
                .designation(designation)
                .status(activeStatus)
                .empCode(empCode)
                .fullName(request.getFullName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .salaryType(request.getSalaryType())
                .joiningDate(request.getJoiningDate())
                .thumbDeviceId(blankToNull(request.getThumbDeviceId()))
                .build();
        user = userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();
        userRoleRepository.save(userRole);

        // Seed annual leave balances for the current year. Safe to call even
        // when the company has zero leave types — the service no-ops. The
        // user we just persisted is, by construction, in the caller's
        // company, so no extra authorization check is needed.
        leaveService.initializeLeaveBalancesForEmployee(user);

        log.info("Created employee id={} empCode={} in company={}", user.getId(), user.getEmpCode(), companyId);
        return toResponse(user, List.of(role.getName()), temporaryPassword);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> getAllEmployees(
            Long departmentId, Long statusId, String search, Pageable pageable) {
        Long companyId = requireCallerCompanyId();
        String trimmedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<User> users = userRepository.searchByCompany(
                companyId, departmentId, statusId, trimmedSearch, pageable);
        if (users.isEmpty()) {
            return PageResponse.from(users.map(u -> toResponse(u, Collections.emptyList(), null)));
        }

        // Fetch roles only for the users on this page — keeps the join
        // small and avoids loading roles for everyone in the company.
        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Map<Long, List<String>> rolesByUser = userRoleRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getUser().getId(),
                        Collectors.mapping(ur -> ur.getRole().getName(), Collectors.toList())
                ));

        return PageResponse.from(users.map(u ->
                toResponse(u, rolesByUser.getOrDefault(u.getId(), Collections.emptyList()), null)));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompanyOrSuperadmin(user);
        return toResponse(user, fetchRoleNames(user.getId()), null);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompanyOrSuperadmin(user);

        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;

        if (request.getDepartmentId() != null) {
            if (companyId == null) {
                throw new BadRequestException("Cannot assign department — employee has no company");
            }
            Department department = departmentRepository
                    .findByIdAndCompanyId(request.getDepartmentId(), companyId)
                    .orElseThrow(() -> new BadRequestException("Department does not belong to employee's company"));
            user.setDepartment(department);
        }
        if (request.getDesignationId() != null) {
            if (companyId == null) {
                throw new BadRequestException("Cannot assign designation — employee has no company");
            }
            Designation designation = designationRepository
                    .findByIdAndCompanyId(request.getDesignationId(), companyId)
                    .orElseThrow(() -> new BadRequestException("Designation does not belong to employee's company"));
            user.setDesignation(designation);
        }
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getSalaryType() != null) {
            user.setSalaryType(request.getSalaryType());
        }
        if (request.getJoiningDate() != null) {
            user.setJoiningDate(request.getJoiningDate());
        }
        if (request.getThumbDeviceId() != null) {
            user.setThumbDeviceId(blankToNull(request.getThumbDeviceId()));
        }
        if (request.getProfilePicUrl() != null) {
            user.setProfilePicUrl(blankToNull(request.getProfilePicUrl()));
        }
        boolean roleChanged = false;
        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
            roleService.ensureAssignable(role);
            userRoleRepository.deleteByUser_Id(user.getId());
            userRoleRepository.save(UserRole.builder().user(user).role(role).build());
            roleChanged = true;
        }

        user = userRepository.save(user);
        if (roleChanged) {
            // Roles are embedded in JWTs — revoke so the next request picks up the new role.
            tokenRevocationService.revokeAllForUser(user.getId());
        }
        log.info("Updated employee id={}", user.getId());
        return toResponse(user, fetchRoleNames(user.getId()), null);
    }

    @Transactional
    public EmployeeResponse updateEmployeeStatus(Long id, UpdateEmployeeStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompanyOrSuperadmin(user);

        UserStatus status = userStatusRepository.findById(request.getStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Status not found"));
        user.setStatus(status);
        user = userRepository.save(user);

        if (status.getName() != null
                && TOKEN_REVOKE_STATUSES.contains(status.getName().trim().toLowerCase(Locale.ROOT))) {
            tokenRevocationService.revokeAllForUser(user.getId());
        }

        log.info("Updated employee id={} status to {}", user.getId(), status.getName());
        return toResponse(user, fetchRoleNames(user.getId()), null);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getMyProfile() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        return toResponse(user, fetchRoleNames(user.getId()), null);
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        return companyId;
    }

    private void ensureSameCompanyOrSuperadmin(User user) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            return;
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        Long targetCompanyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (callerCompanyId == null || !callerCompanyId.equals(targetCompanyId)) {
            log.warn("Cross-company access blocked — caller company={} target company={}",
                    callerCompanyId, targetCompanyId);
            throw new UnauthorizedException("You do not have access to this employee");
        }
    }

    private List<String> fetchRoleNames(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getName())
                .toList();
    }

    /**
     * Uses FE-supplied code when present; otherwise allocates the next free
     * {@code EMPnnn} within the company.
     */
    private String resolveEmpCode(String requested, Long companyId) {
        if (requested != null && !requested.isBlank()) {
            String code = requested.trim();
            if (userRepository.existsByEmpCodeAndCompanyId(code, companyId)) {
                throw new ConflictException(
                        "Employee code already exists in this company",
                        Map.of("empCode", "Employee code already exists in this company"));
            }
            return code;
        }
        long base = userRepository.countByCompanyId(companyId) + 1;
        for (long n = base; n < base + 10_000; n++) {
            String code = String.format("EMP%03d", n);
            if (!userRepository.existsByEmpCodeAndCompanyId(code, companyId)) {
                return code;
            }
        }
        throw new ConflictException("Unable to allocate a unique employee code");
    }

    private EmployeeResponse toResponse(User user, List<String> roles, String temporaryPassword) {
        return EmployeeResponse.builder()
                .id(user.getId())
                .empCode(user.getEmpCode())
                .fullName(user.getFullName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .salaryType(user.getSalaryType() != null ? user.getSalaryType().name() : null)
                .joiningDate(user.getJoiningDate())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .designationId(user.getDesignation() != null ? user.getDesignation().getId() : null)
                .designationName(user.getDesignation() != null ? user.getDesignation().getName() : null)
                .statusId(user.getStatus() != null ? user.getStatus().getId() : null)
                .statusName(user.getStatus() != null ? user.getStatus().getName() : null)
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .roles(roles != null ? roles : new ArrayList<>())
                .avatar(blankToNull(user.getProfilePicUrl()))
                .thumbDeviceId(user.getThumbDeviceId())
                .temporaryPassword(temporaryPassword)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
