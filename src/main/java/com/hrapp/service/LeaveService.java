package com.hrapp.service;

import com.hrapp.dto.request.ActionLeaveRequest;
import com.hrapp.dto.request.ApplyLeaveRequest;
import com.hrapp.dto.response.LeaveBalanceResponse;
import com.hrapp.dto.response.LeaveRequestResponse;
import com.hrapp.entity.Attendance;
import com.hrapp.entity.CompanySettings;
import com.hrapp.entity.Holiday;
import com.hrapp.entity.LeaveBalance;
import com.hrapp.entity.LeaveRequest;
import com.hrapp.entity.LeaveType;
import com.hrapp.entity.User;
import com.hrapp.enums.AttendanceSource;
import com.hrapp.enums.AttendanceStatus;
import com.hrapp.enums.LeaveCountType;
import com.hrapp.enums.LeaveRequestStatus;
import com.hrapp.exception.BadRequestException;
import com.hrapp.exception.ConflictException;
import com.hrapp.exception.ResourceNotFoundException;
import com.hrapp.exception.UnauthorizedException;
import com.hrapp.repository.AttendanceRepository;
import com.hrapp.repository.CompanySettingsRepository;
import com.hrapp.repository.HolidayRepository;
import com.hrapp.repository.LeaveBalanceRepository;
import com.hrapp.repository.LeaveRequestRepository;
import com.hrapp.repository.LeaveTypeRepository;
import com.hrapp.repository.UserRepository;
import com.hrapp.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * End-to-end leave management:
 * <ul>
 *   <li>seed annual balances on employee create / year rollover,</li>
 *   <li>apply / approve / reject / cancel leaves with balance accounting,</li>
 *   <li>auto-mirror approved leaves into the {@code attendance} table.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private static final List<LeaveRequestStatus> ACTIVE_LEAVE_STATUSES =
            List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final HolidayRepository holidayRepository;

    // ============================================================
    //  Balance initialization
    // ============================================================

    /**
     * Low-level balance seeder. Caller is responsible for any company / role
     * authorization — by the time we reach here we trust that {@code user} is
     * legitimately in scope for the caller. Idempotent: skips leave types
     * that already have a balance row for the current year.
     */
    @Transactional
    public List<LeaveBalanceResponse> initializeLeaveBalancesForEmployee(User user) {
        Long userId = user.getId();
        if (user.getCompany() == null) {
            log.warn("Skipping leave-balance init — user {} has no company", userId);
            return List.of();
        }
        Long companyId = user.getCompany().getId();
        int year = LocalDate.now().getYear();

        List<LeaveType> leaveTypes = leaveTypeRepository.findByCompanyId(companyId);
        for (LeaveType leaveType : leaveTypes) {
            boolean exists = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeIdAndYear(userId, leaveType.getId(), year)
                    .isPresent();
            if (exists) {
                continue;
            }
            BigDecimal quota = BigDecimal.valueOf(leaveType.getAnnualQuota()).setScale(1, RoundingMode.HALF_UP);
            LeaveBalance balance = LeaveBalance.builder()
                    .user(user)
                    .leaveType(leaveType)
                    .year(year)
                    .totalDays(quota)
                    .usedDays(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                    .remainingDays(quota)
                    .build();
            leaveBalanceRepository.save(balance);
        }
        log.info("Initialized {} leave balances for user={} year={}", leaveTypes.size(), userId, year);
        return leaveBalanceRepository.findByUserIdAndYear(userId, year).stream()
                .map(this::toBalanceResponse)
                .toList();
    }

    /**
     * Manual re-initialization entry point for ADMIN / HR. Loads and
     * validates the target employee (strict same-company check, no
     * SUPERADMIN bypass) before delegating to
     * {@link #initializeLeaveBalancesForEmployee(User)}.
     */
    @Transactional
    public List<LeaveBalanceResponse> initializeLeaveBalancesManually(Long userId) {
        Long callerCompanyId = requireCallerCompanyId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(user, callerCompanyId);
        return initializeLeaveBalancesForEmployee(user);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getMyLeaveBalances(Integer year) {
        Long userId = requireCallerUserId();
        return leaveBalanceRepository.findByUserIdAndYear(userId, year).stream()
                .map(this::toBalanceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getEmployeeLeaveBalances(Long employeeId, Integer year) {
        Long companyId = requireCallerCompanyId();
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        ensureSameCompany(employee, companyId);
        return leaveBalanceRepository.findByUserIdAndYear(employeeId, year).stream()
                .map(this::toBalanceResponse)
                .toList();
    }

    // ============================================================
    //  Apply / Action / Cancel
    // ============================================================

    @Transactional
    public LeaveRequestResponse applyLeave(ApplyLeaveRequest request) {
        Long userId = requireCallerUserId();
        Long companyId = requireCallerCompanyId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate today = LocalDate.now();
        // Past leaves are allowed up to 30 days back (e.g. HR back-filling a
        // sudden sick day); anything older is almost certainly a typo or
        // attempt to retroactively rewrite history.
        if (request.getFromDate().isBefore(today.minusDays(30))) {
            throw new BadRequestException("Cannot apply leave for dates older than 30 days");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new BadRequestException("To date cannot be before from date");
        }

        boolean isHalfDay = Boolean.TRUE.equals(request.getIsHalfDay());
        if (isHalfDay) {
            if (!request.getFromDate().equals(request.getToDate())) {
                throw new BadRequestException("Half day must be a single day");
            }
            if (request.getHalfDayType() == null) {
                throw new BadRequestException("Half day type is required for half-day leave");
            }
        }

        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(request.getLeaveTypeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        if (isHalfDay && !Boolean.TRUE.equals(leaveType.getAllowHalfDay())) {
            throw new BadRequestException("Half day is not allowed for this leave type");
        }

        CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException("Company settings not configured"));

        BigDecimal totalDays = calculateTotalDays(
                request.getFromDate(), request.getToDate(), isHalfDay, settings, companyId);
        if (totalDays.signum() <= 0) {
            throw new BadRequestException("Selected date range has no working days");
        }

        boolean overlaps = leaveRequestRepository
                .existsByUserIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        userId, ACTIVE_LEAVE_STATUSES, request.getToDate(), request.getFromDate());
        if (overlaps) {
            throw new ConflictException("You already have a leave request for these dates");
        }

        int year = request.getFromDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndLeaveTypeIdAndYear(userId, leaveType.getId(), year)
                .orElseThrow(() -> new BadRequestException("Leave balance not initialized"));
        if (balance.getRemainingDays().compareTo(totalDays) < 0) {
            throw new BadRequestException("Insufficient leave balance");
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .user(user)
                .leaveType(leaveType)
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .totalDays(totalDays)
                .reason(request.getReason())
                .isHalfDay(isHalfDay)
                .halfDayType(isHalfDay ? request.getHalfDayType().name() : null)
                .status(LeaveRequestStatus.PENDING)
                .build();
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        log.info("Leave applied id={} user={} type={} days={}",
                leaveRequest.getId(), userId, leaveType.getName(), totalDays);
        return toRequestResponse(leaveRequest);
    }

    @Transactional
    public LeaveRequestResponse actionLeave(Long id, ActionLeaveRequest request) {
        Long companyId = requireCallerCompanyId();
        Long actionerId = requireCallerUserId();

        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        ensureSameCompany(leaveRequest.getUser(), companyId);

        if (leaveRequest.getStatus() != LeaveRequestStatus.PENDING) {
            throw new BadRequestException("Leave is not pending");
        }
        if (request.getStatus() != LeaveRequestStatus.APPROVED
                && request.getStatus() != LeaveRequestStatus.REJECTED) {
            throw new BadRequestException("Status must be APPROVED or REJECTED");
        }
        if (request.getStatus() == LeaveRequestStatus.REJECTED
                && (request.getRejectReason() == null || request.getRejectReason().isBlank())) {
            throw new BadRequestException("Reject reason is required when rejecting a leave");
        }

        if (request.getStatus() == LeaveRequestStatus.APPROVED) {
            CompanySettings settings = companySettingsRepository.findByCompanyId(companyId)
                    .orElseThrow(() -> new BadRequestException("Company settings not configured"));
            List<LocalDate> holidayDates = fetchHolidayDates(
                    companyId, leaveRequest.getFromDate(), leaveRequest.getToDate());

            int year = leaveRequest.getFromDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeIdAndYear(
                            leaveRequest.getUser().getId(), leaveRequest.getLeaveType().getId(), year)
                    .orElseThrow(() -> new BadRequestException("Leave balance not initialized"));
            if (balance.getRemainingDays().compareTo(leaveRequest.getTotalDays()) < 0) {
                throw new BadRequestException("Insufficient leave balance to approve");
            }
            balance.setRemainingDays(balance.getRemainingDays().subtract(leaveRequest.getTotalDays()));
            balance.setUsedDays(balance.getUsedDays().add(leaveRequest.getTotalDays()));
            leaveBalanceRepository.save(balance);

            autoUpdateAttendance(
                    leaveRequest.getUser(),
                    leaveRequest.getFromDate(),
                    leaveRequest.getToDate(),
                    Boolean.TRUE.equals(leaveRequest.getIsHalfDay()),
                    settings,
                    holidayDates);
        } else {
            leaveRequest.setRejectReason(request.getRejectReason());
        }

        User actioner = userRepository.findById(actionerId)
                .orElseThrow(() -> new ResourceNotFoundException("Actioning user not found"));
        leaveRequest.setStatus(request.getStatus());
        leaveRequest.setActionedBy(actioner);
        leaveRequest.setActionedAt(java.time.LocalDateTime.now());
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        log.info("Leave id={} {} by user={}", id, request.getStatus(), actionerId);
        return toRequestResponse(leaveRequest);
    }

    @Transactional
    public LeaveRequestResponse cancelLeave(Long id) {
        Long userId = requireCallerUserId();
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        if (!leaveRequest.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You can only cancel your own leaves");
        }
        return cancelInternal(leaveRequest, "Cancelled by employee");
    }

    @Transactional
    public LeaveRequestResponse adminCancelLeave(Long id) {
        Long companyId = requireCallerCompanyId();
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        ensureSameCompany(leaveRequest.getUser(), companyId);
        return cancelInternal(leaveRequest, "Cancelled by admin");
    }

    private LeaveRequestResponse cancelInternal(LeaveRequest leaveRequest, String reason) {
        LeaveRequestStatus status = leaveRequest.getStatus();
        if (status == LeaveRequestStatus.REJECTED) {
            throw new BadRequestException("Leave already cancelled or rejected");
        }

        if (status == LeaveRequestStatus.APPROVED) {
            int year = leaveRequest.getFromDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeIdAndYear(
                            leaveRequest.getUser().getId(),
                            leaveRequest.getLeaveType().getId(),
                            year)
                    .orElse(null);
            if (balance != null) {
                balance.setRemainingDays(balance.getRemainingDays().add(leaveRequest.getTotalDays()));
                BigDecimal newUsed = balance.getUsedDays().subtract(leaveRequest.getTotalDays());
                balance.setUsedDays(newUsed.signum() < 0 ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : newUsed);
                leaveBalanceRepository.save(balance);
            }
            restoreAttendance(
                    leaveRequest.getUser().getId(),
                    leaveRequest.getFromDate(),
                    leaveRequest.getToDate());
        }

        leaveRequest.setStatus(LeaveRequestStatus.REJECTED);
        leaveRequest.setRejectReason(reason);
        leaveRequest.setActionedAt(java.time.LocalDateTime.now());
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        log.info("Leave id={} cancelled ({})", leaveRequest.getId(), reason);
        return toRequestResponse(leaveRequest);
    }

    // ============================================================
    //  Listings
    // ============================================================

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getMyLeaveRequests(Integer month, Integer year) {
        Long userId = requireCallerUserId();
        YearMonth ym = YearMonth.of(year, month);
        return leaveRequestRepository
                .findByUserIdAndFromDateBetween(userId, ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .sorted(Comparator.comparing(LeaveRequest::getAppliedAt).reversed())
                .map(this::toRequestResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getPendingLeaveRequests() {
        Long companyId = requireCallerCompanyId();
        return leaveRequestRepository
                .findByUser_CompanyIdAndStatus(companyId, LeaveRequestStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(LeaveRequest::getAppliedAt))
                .map(this::toRequestResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getCompanyLeaveRequests(Integer month, Integer year) {
        Long companyId = requireCallerCompanyId();
        YearMonth ym = YearMonth.of(year, month);
        return leaveRequestRepository
                .findByUser_CompanyIdAndFromDateBetween(companyId, ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .sorted(Comparator.comparing(LeaveRequest::getAppliedAt).reversed())
                .map(this::toRequestResponse)
                .toList();
    }

    // ============================================================
    //  Year-end carry-forward
    // ============================================================

    @Transactional
    public void processYearEndCarryForward(Long companyId, Integer year) {
        List<LeaveBalance> balances = leaveBalanceRepository.findByUser_CompanyIdAndYear(companyId, year);
        int nextYear = year + 1;
        int created = 0;
        for (LeaveBalance balance : balances) {
            LeaveType leaveType = balance.getLeaveType();
            Long userId = balance.getUser().getId();

            boolean exists = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeIdAndYear(userId, leaveType.getId(), nextYear)
                    .isPresent();
            if (exists) {
                continue;
            }

            BigDecimal annualQuota = BigDecimal.valueOf(leaveType.getAnnualQuota())
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal totalDays = annualQuota;

            if (Boolean.TRUE.equals(leaveType.getCarryForward())) {
                BigDecimal maxCarry = BigDecimal.valueOf(leaveType.getMaxCarryForwardDays())
                        .setScale(1, RoundingMode.HALF_UP);
                BigDecimal carry = balance.getRemainingDays().min(maxCarry).max(BigDecimal.ZERO);
                totalDays = annualQuota.add(carry);
            }

            LeaveBalance next = LeaveBalance.builder()
                    .user(balance.getUser())
                    .leaveType(leaveType)
                    .year(nextYear)
                    .totalDays(totalDays)
                    .usedDays(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                    .remainingDays(totalDays)
                    .build();
            leaveBalanceRepository.save(next);
            created++;
        }
        log.info("Year-end carry-forward for company={} from year={} -> {} balances created",
                companyId, year, created);
    }

    // ============================================================
    //  Helpers — exposed for testability / scheduler clarity
    // ============================================================

    BigDecimal calculateTotalDays(LocalDate from, LocalDate to, boolean isHalfDay,
                                  CompanySettings settings, Long companyId) {
        if (isHalfDay) {
            return new BigDecimal("0.5").setScale(1, RoundingMode.HALF_UP);
        }

        LeaveCountType countType = resolveCountType(settings.getLeaveCountType());
        DayOfWeek weekOff = parseDayOfWeek(settings.getWeekOffDay());
        Set<LocalDate> holidays = countType == LeaveCountType.EXCLUDE_WEEK_OFF_AND_HOLIDAYS
                ? new HashSet<>(fetchHolidayDates(companyId, from, to))
                : Set.of();

        long count = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            boolean skip = false;
            if (countType != LeaveCountType.CALENDAR_DAYS && weekOff != null
                    && cursor.getDayOfWeek() == weekOff) {
                skip = true;
            }
            if (!skip && countType == LeaveCountType.EXCLUDE_WEEK_OFF_AND_HOLIDAYS
                    && holidays.contains(cursor)) {
                skip = true;
            }
            if (!skip) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return BigDecimal.valueOf(count).setScale(1, RoundingMode.HALF_UP);
    }

    boolean isWorkingDay(LocalDate date, CompanySettings settings, List<LocalDate> holidays) {
        DayOfWeek weekOff = parseDayOfWeek(settings.getWeekOffDay());
        if (weekOff != null && date.getDayOfWeek() == weekOff) {
            return false;
        }
        return holidays == null || !holidays.contains(date);
    }

    void autoUpdateAttendance(User user, LocalDate from, LocalDate to, boolean isHalfDay,
                              CompanySettings settings, List<LocalDate> holidayDates) {
        AttendanceStatus newStatus = isHalfDay ? AttendanceStatus.HALF_DAY : AttendanceStatus.ON_LEAVE;
        LocalDate endDate = isHalfDay ? from : to;
        LocalDate cursor = from;
        while (!cursor.isAfter(endDate)) {
            if (!isWorkingDay(cursor, settings, holidayDates)) {
                cursor = cursor.plusDays(1);
                continue;
            }
            final LocalDate date = cursor;
            Attendance attendance = attendanceRepository
                    .findByUserIdAndAttendanceDate(user.getId(), date)
                    .orElseGet(() -> Attendance.builder()
                            .user(user)
                            .company(user.getCompany())
                            .attendanceDate(date)
                            .workedHours(BigDecimal.ZERO)
                            .overtimeHours(BigDecimal.ZERO)
                            .isManual(false)
                            .build());
            attendance.setStatus(newStatus);
            attendance.setSource(AttendanceSource.MANUAL);
            attendance.setIsManual(false);
            attendanceRepository.save(attendance);
            cursor = cursor.plusDays(1);
        }
    }

    private void restoreAttendance(Long userId, LocalDate from, LocalDate to) {
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Optional<Attendance> opt = attendanceRepository
                    .findByUserIdAndAttendanceDate(userId, cursor);
            if (opt.isPresent()) {
                Attendance attendance = opt.get();
                if (attendance.getStatus() == AttendanceStatus.ON_LEAVE
                        || attendance.getStatus() == AttendanceStatus.HALF_DAY) {
                    attendance.setStatus(AttendanceStatus.ABSENT);
                    attendanceRepository.save(attendance);
                }
            }
            cursor = cursor.plusDays(1);
        }
    }

    private List<LocalDate> fetchHolidayDates(Long companyId, LocalDate from, LocalDate to) {
        return holidayRepository.findByCompanyIdAndHolidayDateBetween(companyId, from, to).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private LeaveCountType resolveCountType(String value) {
        if (value == null || value.isBlank()) {
            return LeaveCountType.EXCLUDE_WEEK_OFF_AND_HOLIDAYS;
        }
        try {
            return LeaveCountType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid leave_count_type '{}', defaulting to EXCLUDE_WEEK_OFF_AND_HOLIDAYS", value);
            return LeaveCountType.EXCLUDE_WEEK_OFF_AND_HOLIDAYS;
        }
    }

    private DayOfWeek parseDayOfWeek(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid week_off_day '{}'", value);
            return null;
        }
    }

    private Long requireCallerUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }

    private Long requireCallerCompanyId() {
        Long companyId = SecurityUtil.getCurrentUserCompanyId();
        if (companyId == null) {
            throw new BadRequestException("Caller is not bound to a company");
        }
        return companyId;
    }

    private void ensureSameCompany(User user, Long callerCompanyId) {
        Long userCompanyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (!callerCompanyId.equals(userCompanyId)) {
            log.warn("Cross-company leave access blocked — caller={} target={}",
                    callerCompanyId, userCompanyId);
            throw new UnauthorizedException("Employee does not belong to your company");
        }
    }

    // ============================================================
    //  Mappers
    // ============================================================

    private LeaveBalanceResponse toBalanceResponse(LeaveBalance balance) {
        LeaveType type = balance.getLeaveType();
        User user = balance.getUser();
        return LeaveBalanceResponse.builder()
                .id(balance.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .leaveTypeId(type != null ? type.getId() : null)
                .leaveTypeName(type != null ? type.getName() : null)
                .isPaid(type != null ? type.getIsPaid() : null)
                .allowHalfDay(type != null ? type.getAllowHalfDay() : null)
                .carryForward(type != null ? type.getCarryForward() : null)
                .year(balance.getYear())
                .totalDays(balance.getTotalDays())
                .usedDays(balance.getUsedDays())
                .remainingDays(balance.getRemainingDays())
                .build();
    }

    private LeaveRequestResponse toRequestResponse(LeaveRequest leaveRequest) {
        User user = leaveRequest.getUser();
        LeaveType type = leaveRequest.getLeaveType();
        User actioner = leaveRequest.getActionedBy();
        return LeaveRequestResponse.builder()
                .id(leaveRequest.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .empCode(user != null ? user.getEmpCode() : null)
                .leaveTypeId(type != null ? type.getId() : null)
                .leaveTypeName(type != null ? type.getName() : null)
                .fromDate(leaveRequest.getFromDate())
                .toDate(leaveRequest.getToDate())
                .totalDays(leaveRequest.getTotalDays())
                .isHalfDay(leaveRequest.getIsHalfDay())
                .halfDayType(leaveRequest.getHalfDayType())
                .reason(leaveRequest.getReason())
                .status(leaveRequest.getStatus() != null ? leaveRequest.getStatus().name() : null)
                .rejectReason(leaveRequest.getRejectReason())
                .actionedByName(actioner != null ? actioner.getFullName() : null)
                .appliedAt(leaveRequest.getAppliedAt())
                .actionedAt(leaveRequest.getActionedAt())
                .build();
    }
}
