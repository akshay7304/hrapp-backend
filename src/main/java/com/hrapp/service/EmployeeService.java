package com.hrapp.service;

import com.hrapp.dto.request.CreateEmployeeRequest;
import com.hrapp.dto.request.UpdateEmployeeRequest;
import com.hrapp.dto.request.UpdateEmployeeStatusRequest;
import com.hrapp.dto.response.EmployeeResponse;
import com.hrapp.dto.response.EmployeeSetupResponse;
import com.hrapp.dto.response.PageResponse;
import com.hrapp.dto.response.SalaryAdjustmentResponse;
import com.hrapp.dto.response.SalaryStructureResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.Department;
import com.hrapp.entity.Designation;
import com.hrapp.entity.Role;
import com.hrapp.entity.User;
import com.hrapp.entity.UserRole;
import com.hrapp.entity.UserStatus;
import com.hrapp.enums.SalaryType;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.DepartmentRepository;
import com.hrapp.repository.DesignationRepository;
import com.hrapp.repository.EmployeeJdbcRepository;
import com.hrapp.repository.EmployeeRow;
import com.hrapp.repository.RoleRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.repository.UserRoleRepository;
import com.hrapp.repository.UserStatusRepository;
import com.hrapp.security.SecurityUtil;
import com.hrapp.security.TokenRevocationService;
import com.hrapp.util.TempPasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Employee CRUD bounded by the caller's company.
 * Reads that join department, designation, status, and roles go through
 * {@link EmployeeJdbcRepository}. Writes stay on JPA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private static final int MAX_PAGE_SIZE = 100;
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
    private final EmployeeJdbcRepository employeeJdbcRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeaveService leaveService;
    private final PayrollService payrollService;
    private final TokenRevocationService tokenRevocationService;
    private final RoleService roleService;
    private final DepartmentService departmentService;
    private final DesignationService designationService;

    @Transactional(readOnly = true)
    public EmployeeSetupResponse getEmployeeSetup() {
        requireCallerCompanyId();
        return EmployeeSetupResponse.builder()
                .departments(departmentService.getAllDepartments())
                .designations(designationService.getAllDesignations())
                .roles(roleService.getAssignableRoles())
                .salaryTypes(Arrays.stream(SalaryType.values()).map(Enum::name).toList())
                .build();
    }

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        Long companyId = requireCallerCompanyId();

        if (userRepository.existsByMobile(request.getMobile())) {
            throw new ConflictException(
                    "Mobile number is already registered",
                    Map.of("mobile", "Mobile number is already registered"));
        }

        String empCode = resolveEmployeeCode(request.getEmpCode(), companyId);
        Department department = requireDepartment(
                request.getDepartmentId(), companyId, "Department does not belong to your company");
        Designation designation = requireDesignation(
                request.getDesignationId(), companyId, "Designation does not belong to your company");
        UserStatus activeStatus = userStatusRepository.findByName(STATUS_ACTIVE)
                .orElseThrow(() -> new BadRequestException("'" + STATUS_ACTIVE + "' status is not configured"));
        Role role = requireAssignableRole(request.getRoleId());
        Company company = companyRepository.findByIdAndIsActive(companyId, true)
                .orElseThrow(() -> new BadRequestException("Company is inactive or does not exist"));

        String temporaryPassword = TempPasswordUtil.generate(10);
        User user = userRepository.save(User.builder()
                .company(company)
                .department(department)
                .designation(designation)
                .status(activeStatus)
                .empCode(empCode)
                .fullName(request.getFullName().trim())
                .mobile(request.getMobile())
                .email(blankToNull(request.getEmail()))
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .salaryType(request.getSalaryType())
                .joiningDate(request.getJoiningDate())
                .thumbDeviceId(blankToNull(request.getThumbDeviceId()))
                .build());

        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
        leaveService.initializeLeaveBalancesForEmployee(user);
        if (request.getSalaryStructure() != null) {
            payrollService.createStructureForNewEmployee(user, request.getSalaryStructure());
        }

        log.info("Created employee id={} empCode={} in company={}", user.getId(), user.getEmpCode(), companyId);
        userRepository.flush();
        EmployeeResponse response = toEmployeeResponse(requireEmployeeById(user.getId()));
        response.setTemporaryPassword(temporaryPassword);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> getAllEmployees(
            Long departmentId, Long statusId, String search, int page, int size) {
        validateEmployeeListPage(page, size);
        Long companyId = requireCallerCompanyId();
        String trimmedSearch = blankToNull(search);
        long offset = (long) page * size;
        List<EmployeeResponse> content = employeeJdbcRepository
                .getAllEmployees(companyId, departmentId, statusId, trimmedSearch, size, offset)
                .stream()
                .map(this::toEmployeeResponse)
                .toList();
        long total = employeeJdbcRepository.getAllEmployeesCount(companyId, departmentId, statusId, trimmedSearch);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {
        EmployeeRow row = requireEmployeeById(id);
        ensureEmployeeBelongsToCallerCompany(row.companyId());
        return toEmployeeResponse(row);
    }

    @Transactional(readOnly = true)
    public List<SalaryStructureResponse> getEmployeeSalaryStructure(Long employeeId) {
        return payrollService.getSalaryStructure(employeeId);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdjustmentResponse> getEmployeeAdjustments(Long employeeId, Integer month, Integer year) {
        if ((month == null) != (year == null)) {
            throw new BadRequestException("Provide both month and year, or omit both");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BadRequestException("Month must be between 1 and 12");
        }
        return payrollService.getAdjustments(employeeId, month, year);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        User user = requireEmployeeInCallerCompany(id);
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;

        if (request.getDepartmentId() != null) {
            user.setDepartment(requireDepartment(
                    request.getDepartmentId(),
                    requireEmployeeCompanyId(companyId, "department"),
                    "Department does not belong to employee's company"));
        }
        if (request.getDesignationId() != null) {
            user.setDesignation(requireDesignation(
                    request.getDesignationId(),
                    requireEmployeeCompanyId(companyId, "designation"),
                    "Designation does not belong to employee's company"));
        }
        if (request.getFullName() != null) {
            String fullName = request.getFullName().trim();
            if (fullName.isEmpty()) {
                throw new BadRequestException("Full name is required");
            }
            user.setFullName(fullName);
        }
        if (request.getEmail() != null) {
            user.setEmail(blankToNull(request.getEmail()));
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

        boolean roleChanged = replaceEmployeeRole(user, request.getRoleId());
        userRepository.save(user);
        if (roleChanged) {
            tokenRevocationService.revokeAllForUser(user.getId());
        }
        log.info("Updated employee id={}", user.getId());
        userRepository.flush();
        return toEmployeeResponse(requireEmployeeById(user.getId()));
    }

    @Transactional
    public EmployeeResponse updateEmployeeStatus(Long id, UpdateEmployeeStatusRequest request) {
        User user = requireEmployeeInCallerCompany(id);
        UserStatus status = userStatusRepository.findById(request.getStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Status not found"));
        user.setStatus(status);
        userRepository.save(user);

        if (status.getName() != null
                && TOKEN_REVOKE_STATUSES.contains(status.getName().trim().toLowerCase(Locale.ROOT))) {
            tokenRevocationService.revokeAllForUser(user.getId());
        }
        log.info("Updated employee id={} status to {}", user.getId(), status.getName());
        userRepository.flush();
        return toEmployeeResponse(requireEmployeeById(user.getId()));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getMyProfile() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return employeeJdbcRepository.getEmployeeById(userId)
                .map(this::toEmployeeResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    private User requireEmployeeInCallerCompany(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        ensureEmployeeBelongsToCallerCompany(companyId);
        return user;
    }

    private EmployeeRow requireEmployeeById(Long id) {
        return employeeJdbcRepository.getEmployeeById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private Department requireDepartment(Long departmentId, Long companyId, String message) {
        return departmentRepository.findByIdAndCompanyId(departmentId, companyId)
                .orElseThrow(() -> new BadRequestException(message));
    }

    private Designation requireDesignation(Long designationId, Long companyId, String message) {
        return designationRepository.findByIdAndCompanyId(designationId, companyId)
                .orElseThrow(() -> new BadRequestException(message));
    }

    private Role requireAssignableRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        roleService.ensureAssignable(role);
        return role;
    }

    /**
     * Replaces the employee's roles with a single assignable role.
     * No-op when that role is already the only one assigned.
     *
     * @return true when the stored role actually changed
     */
    private boolean replaceEmployeeRole(User user, Long roleId) {
        if (roleId == null) {
            return false;
        }
        Role role = requireAssignableRole(roleId);
        List<UserRole> current = userRoleRepository.findByUserId(user.getId());
        if (current.size() == 1 && role.getId().equals(current.get(0).getRole().getId())) {
            return false;
        }
        if (!current.isEmpty()) {
            userRoleRepository.deleteAll(current);
            userRoleRepository.flush();
        }
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
        return true;
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        return companyId;
    }

    private static Long requireEmployeeCompanyId(Long companyId, String assignment) {
        if (companyId == null) {
            throw new BadRequestException("Cannot assign " + assignment + " — employee has no company");
        }
        return companyId;
    }

    private void ensureEmployeeBelongsToCallerCompany(Long targetCompanyId) {
        if (SecurityUtil.hasRole(ROLE_SUPERADMIN)) {
            return;
        }
        Long callerCompanyId = SecurityUtil.getCurrentUserCompanyId();
        if (callerCompanyId == null || !callerCompanyId.equals(targetCompanyId)) {
            log.warn("Cross-company access blocked — caller company={} target company={}",
                    callerCompanyId, targetCompanyId);
            throw new UnauthorizedException("You do not have access to this employee");
        }
    }

    private String resolveEmployeeCode(String requested, Long companyId) {
        if (requested != null && !requested.isBlank()) {
            String code = requested.trim();
            if (userRepository.existsByEmpCodeAndCompanyId(code, companyId)) {
                throw new ConflictException(
                        "Employee code already exists in this company",
                        Map.of("empCode", "Employee code already exists in this company"));
            }
            return code;
        }
        long sequence = employeeJdbcRepository.getNextEmployeeCodeSequence(companyId);
        return String.format("EMP%03d", sequence);
    }

    private static void validateEmployeeListPage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private EmployeeResponse toEmployeeResponse(EmployeeRow row) {
        return EmployeeResponse.builder()
                .id(row.id())
                .empCode(row.empCode())
                .fullName(row.fullName())
                .mobile(row.mobile())
                .email(row.email())
                .salaryType(row.salaryType())
                .joiningDate(row.joiningDate())
                .departmentId(row.departmentId())
                .departmentName(row.departmentName())
                .designationId(row.designationId())
                .designationName(row.designationName())
                .statusId(row.statusId())
                .statusName(row.statusName())
                .companyId(row.companyId())
                .roles(splitEmployeeRoleNames(row.roleNames()))
                .avatar(blankToNull(row.profilePicUrl()))
                .thumbDeviceId(row.thumbDeviceId())
                .createdAt(row.createdAt())
                .build();
    }

    private static List<String> splitEmployeeRoleNames(String roleNames) {
        if (roleNames == null || roleNames.isBlank()) {
            return List.of();
        }
        return List.of(roleNames.split(","));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
