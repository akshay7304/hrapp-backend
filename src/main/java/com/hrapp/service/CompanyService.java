package com.hrapp.service;

import com.hrapp.dto.request.CreateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyRequest;
import com.hrapp.dto.request.UpdateCompanyStatusRequest;
import com.hrapp.dto.response.CompanyDetailResponse;
import com.hrapp.dto.response.CompanyResponse;
import com.hrapp.dto.response.DepartmentResponse;
import com.hrapp.dto.response.DesignationResponse;
import com.hrapp.dto.response.HolidayResponse;
import com.hrapp.dto.response.LeaveTypeResponse;
import com.hrapp.entity.Company;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.Department;
import com.hrapp.entity.Designation;
import com.hrapp.entity.Holiday;
import com.hrapp.entity.LeaveType;
import com.hrapp.entity.Role;
import com.hrapp.entity.User;
import com.hrapp.entity.UserRole;
import com.hrapp.entity.UserStatus;
import com.hrapp.enums.SalaryType;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.repository.CompanyRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.DepartmentRepository;
import com.hrapp.repository.DesignationRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.LeaveTypeRepository;
import com.hrapp.repository.RoleRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.repository.UserRoleRepository;
import com.hrapp.repository.UserStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SUPERADMIN-only company onboarding and management. The interesting flow is
 * {@link #createCompany} which atomically: (1) creates a Company row,
 * (2) seeds default departments / designations / leave-types / holidays /
 * settings, (3) creates the founder ADMIN user with mobile-as-password, and
 * (4) initialises the admin's leave balances. Everything happens in one
 * {@code @Transactional} so a failure anywhere rolls the whole thing back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "Active";
    private static final String DEFAULT_HR_DEPARTMENT = "HR";
    private static final String DEFAULT_MANAGER_DESIGNATION = "Manager";

    private static final List<String> DEFAULT_DEPARTMENTS = List.of(
            "Engineering", "HR", "Operations", "Finance", "Sales", "Marketing");

    private static final List<String> DEFAULT_DESIGNATIONS = List.of(
            "Manager", "Senior Engineer", "Junior Engineer",
            "HR Executive", "Accountant", "Operations Executive");

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserStatusRepository userStatusRepository;
    private final LeaveService leaveService;
    private final PasswordEncoder passwordEncoder;

    // ============================================================
    //  Create
    // ============================================================

    @Transactional
    public CompanyDetailResponse createCompany(CreateCompanyRequest request) {
        if (userRepository.existsByMobile(request.getAdminMobile())) {
            throw new ConflictException("Mobile number is already registered");
        }

        Company company = companyRepository.save(Company.builder()
                .name(request.getName())
                .address(request.getAddress())
                .isActive(true)
                .build());
        log.info("Created company id={} name='{}'", company.getId(), company.getName());

        SeededDefaults seeded = seedDefaultData(company);

        Department hrDepartment = seeded.departmentsByName.get(DEFAULT_HR_DEPARTMENT);
        Designation managerDesignation = seeded.designationsByName.get(DEFAULT_MANAGER_DESIGNATION);
        if (hrDepartment == null || managerDesignation == null) {
            // Defensive — DEFAULT_DEPARTMENTS / DEFAULT_DESIGNATIONS guarantee these,
            // but bail loudly if someone re-orders the constants.
            throw new BadRequestException("Default HR department / Manager designation missing");
        }

        UserStatus activeStatus = userStatusRepository.findByName(STATUS_ACTIVE)
                .orElseThrow(() -> new BadRequestException(
                        "'" + STATUS_ACTIVE + "' user status is not configured"));
        Role adminRole = roleRepository.findByName(ROLE_ADMIN)
                .orElseThrow(() -> new BadRequestException(
                        "'" + ROLE_ADMIN + "' role is not configured"));

        User admin = userRepository.save(User.builder()
                .company(company)
                .department(hrDepartment)
                .designation(managerDesignation)
                .status(activeStatus)
                .fullName(request.getAdminFullName())
                .mobile(request.getAdminMobile())
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getAdminMobile()))
                .salaryType(SalaryType.MONTHLY)
                .joiningDate(LocalDate.now())
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(admin)
                .role(adminRole)
                .build());

        // Seed annual leave balances for the new admin. Authorization is
        // implicit: only SUPERADMIN can call createCompany, and the admin
        // user was just created against this brand-new company.
        leaveService.initializeLeaveBalancesForEmployee(admin);

        log.info("Created admin user id={} for company id={}", admin.getId(), company.getId());

        return toDetailResponse(company, seeded, admin, 1);
    }

    // ============================================================
    //  Read
    // ============================================================

    @Transactional(readOnly = true)
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> toCompanyResponse(c, (int) userRepository.countByCompanyId(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyDetailResponse getCompanyById(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        SeededDefaults seeded = new SeededDefaults();
        seeded.departments = departmentRepository.findByCompanyId(id);
        seeded.designations = designationRepository.findByCompanyId(id);
        seeded.leaveTypes = leaveTypeRepository.findByCompanyId(id);
        seeded.holidays = holidayRepository.findByCompanyIdOrderByHolidayDateAsc(id);

        User admin = userRepository.findByCompanyIdAndRoleName(id, ROLE_ADMIN).stream()
                .findFirst()
                .orElse(null);
        int totalEmployees = (int) userRepository.countByCompanyId(id);
        return toDetailResponse(company, seeded, admin, totalEmployees);
    }

    // ============================================================
    //  Update
    // ============================================================

    @Transactional
    public CompanyResponse updateCompany(Long id, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        company.setName(request.getName());
        company.setAddress(request.getAddress());
        company.setLogoUrl(request.getLogoUrl());
        company = companyRepository.save(company);
        log.info("Updated company id={}", company.getId());
        return toCompanyResponse(company, (int) userRepository.countByCompanyId(company.getId()));
    }

    @Transactional
    public CompanyResponse updateCompanyStatus(Long id, UpdateCompanyStatusRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        company.setIsActive(request.getIsActive());
        company = companyRepository.save(company);
        log.info("Updated company id={} isActive={}", company.getId(), company.getIsActive());
        return toCompanyResponse(company, (int) userRepository.countByCompanyId(company.getId()));
    }

    // ============================================================
    //  Default-data seeding
    // ============================================================

    private SeededDefaults seedDefaultData(Company company) {
        SeededDefaults seeded = new SeededDefaults();
        seeded.departments = seedDepartments(company);
        seeded.designations = seedDesignations(company);
        seeded.leaveTypes = seedLeaveTypes(company);
        seeded.holidays = seedHolidays(company);
        seedCompanySettings(company);

        seeded.departments.forEach(d -> seeded.departmentsByName.put(d.getName(), d));
        seeded.designations.forEach(d -> seeded.designationsByName.put(d.getName(), d));
        return seeded;
    }

    private List<Department> seedDepartments(Company company) {
        List<Department> created = new ArrayList<>(DEFAULT_DEPARTMENTS.size());
        for (String name : DEFAULT_DEPARTMENTS) {
            created.add(departmentRepository.save(Department.builder()
                    .company(company)
                    .name(name)
                    .build()));
        }
        return created;
    }

    private List<Designation> seedDesignations(Company company) {
        List<Designation> created = new ArrayList<>(DEFAULT_DESIGNATIONS.size());
        for (String name : DEFAULT_DESIGNATIONS) {
            created.add(designationRepository.save(Designation.builder()
                    .company(company)
                    .name(name)
                    .build()));
        }
        return created;
    }

    private List<LeaveType> seedLeaveTypes(Company company) {
        List<LeaveType> created = new ArrayList<>(4);
        created.add(leaveTypeRepository.save(LeaveType.builder()
                .company(company)
                .name("Casual Leave")
                .annualQuota(12)
                .isPaid(true)
                .allowHalfDay(true)
                .carryForward(false)
                .maxCarryForwardDays(0)
                .build()));
        created.add(leaveTypeRepository.save(LeaveType.builder()
                .company(company)
                .name("Sick Leave")
                .annualQuota(8)
                .isPaid(true)
                .allowHalfDay(false)
                .carryForward(false)
                .maxCarryForwardDays(0)
                .build()));
        created.add(leaveTypeRepository.save(LeaveType.builder()
                .company(company)
                .name("Earned Leave")
                .annualQuota(15)
                .isPaid(true)
                .allowHalfDay(true)
                .carryForward(true)
                .maxCarryForwardDays(15)
                .build()));
        created.add(leaveTypeRepository.save(LeaveType.builder()
                .company(company)
                .name("Unpaid Leave")
                .annualQuota(0)
                .isPaid(false)
                .allowHalfDay(false)
                .carryForward(false)
                .maxCarryForwardDays(0)
                .build()));
        return created;
    }

    private List<Holiday> seedHolidays(Company company) {
        int year = LocalDate.now().getYear();
        List<HolidaySeed> seeds = List.of(
                new HolidaySeed("Republic Day", LocalDate.of(year, 1, 26)),
                new HolidaySeed("Holi", LocalDate.of(year, 3, 14)),
                new HolidaySeed("Good Friday", LocalDate.of(year, 4, 18)),
                new HolidaySeed("Independence Day", LocalDate.of(year, 8, 15)),
                new HolidaySeed("Gandhi Jayanti", LocalDate.of(year, 10, 2)),
                new HolidaySeed("Diwali", LocalDate.of(year, 10, 20)),
                new HolidaySeed("Christmas", LocalDate.of(year, 12, 25))
        );
        List<Holiday> created = new ArrayList<>(seeds.size());
        for (HolidaySeed seed : seeds) {
            created.add(holidayRepository.save(Holiday.builder()
                    .company(company)
                    .name(seed.name())
                    .holidayDate(seed.date())
                    .build()));
        }
        created.sort(Comparator.comparing(Holiday::getHolidayDate));
        return created;
    }

    private void seedCompanySettings(Company company) {
        companySettingsRepository.save(CompanySettings.builder()
                .company(company)
                .shiftStartTime(LocalTime.of(9, 0))
                .shiftEndTime(LocalTime.of(18, 0))
                .shiftHours(new BigDecimal("9.0"))
                .halfDayHours(new BigDecimal("4.5"))
                .overtimeAfterHours(new BigDecimal("9.0"))
                .lateMarkAfterMinutes(15)
                .weekOffDay("SUNDAY")
                .leaveCountType("EXCLUDE_WEEK_OFF_AND_HOLIDAYS")
                .build());
    }

    // ============================================================
    //  Mappers
    // ============================================================

    private CompanyResponse toCompanyResponse(Company company, int totalEmployees) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .address(company.getAddress())
                .logoUrl(company.getLogoUrl())
                .isActive(company.getIsActive())
                .totalEmployees(totalEmployees)
                .createdAt(company.getCreatedAt())
                .build();
    }

    private CompanyDetailResponse toDetailResponse(Company company, SeededDefaults seeded,
                                                   User admin, int totalEmployees) {
        return CompanyDetailResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .address(company.getAddress())
                .logoUrl(company.getLogoUrl())
                .isActive(company.getIsActive())
                .totalEmployees(totalEmployees)
                .createdAt(company.getCreatedAt())
                .departments(seeded.departments.stream().map(this::toDepartmentResponse).toList())
                .designations(seeded.designations.stream().map(this::toDesignationResponse).toList())
                .leaveTypes(seeded.leaveTypes.stream().map(this::toLeaveTypeResponse).toList())
                .holidays(seeded.holidays.stream().map(this::toHolidayResponse).toList())
                .adminName(admin != null ? admin.getFullName() : null)
                .adminMobile(admin != null ? admin.getMobile() : null)
                .build();
    }

    private DepartmentResponse toDepartmentResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .companyId(department.getCompany() != null ? department.getCompany().getId() : null)
                .createdAt(department.getCreatedAt())
                .build();
    }

    private DesignationResponse toDesignationResponse(Designation designation) {
        return DesignationResponse.builder()
                .id(designation.getId())
                .name(designation.getName())
                .companyId(designation.getCompany() != null ? designation.getCompany().getId() : null)
                .createdAt(designation.getCreatedAt())
                .build();
    }

    private LeaveTypeResponse toLeaveTypeResponse(LeaveType leaveType) {
        return LeaveTypeResponse.builder()
                .id(leaveType.getId())
                .name(leaveType.getName())
                .annualQuota(leaveType.getAnnualQuota())
                .isPaid(leaveType.getIsPaid())
                .allowHalfDay(leaveType.getAllowHalfDay())
                .carryForward(leaveType.getCarryForward())
                .maxCarryForwardDays(leaveType.getMaxCarryForwardDays())
                .companyId(leaveType.getCompany() != null ? leaveType.getCompany().getId() : null)
                .createdAt(leaveType.getCreatedAt())
                .build();
    }

    private HolidayResponse toHolidayResponse(Holiday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .name(holiday.getName())
                .holidayDate(holiday.getHolidayDate())
                .companyId(holiday.getCompany() != null ? holiday.getCompany().getId() : null)
                .createdAt(holiday.getCreatedAt())
                .build();
    }

    // ============================================================
    //  Helper structs
    // ============================================================

    /**
     * Tiny mutable carrier so the caller can keep the seeded entities to map
     * back into the detail response without an extra DB round-trip.
     */
    private static final class SeededDefaults {
        List<Department> departments = List.of();
        List<Designation> designations = List.of();
        List<LeaveType> leaveTypes = List.of();
        List<Holiday> holidays = List.of();
        final Map<String, Department> departmentsByName = new LinkedHashMap<>();
        final Map<String, Designation> designationsByName = new LinkedHashMap<>();
    }

    private record HolidaySeed(String name, LocalDate date) {}
}
